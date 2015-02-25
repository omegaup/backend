package com.omegaup.runner

import java.io._
import javax.servlet._
import javax.servlet.http._
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler._
import com.omegaup._
import com.omegaup.data._
import com.omegaup.data.OmegaUpProtocol._
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.archivers.tar.{TarArchiveInputStream, TarArchiveEntry}

import spray.json._

class OmegaUpRunstreamWriter(outputStream: OutputStream)(implicit ctx: Context)
		extends Closeable with RunCaseCallback with Log {
  private val bzip2 = new BZip2CompressorOutputStream(outputStream)
  private val dos = new DataOutputStream(bzip2)
  private var finalized = false

  def apply(filename: String, length: Long, stream: InputStream): Unit = {
    if (finalized) return
    log.debug("Writing {}({}) into runstream", filename, length)
    dos.writeBoolean(true)
    dos.writeUTF(filename)
    dos.writeLong(length)
    val buffer = new Array[Byte](1024)
    var read = 0
    while ( { read = stream.read(buffer, 0, buffer.length); read > 0 } ) {
      dos.write(buffer, 0, read)
    }
    dos.flush
  }

  def finalize(message: RunOutputMessage): Unit = {
    if (finalized) return
    log.debug("Finalizing runstream with {}", message)
    dos.writeBoolean(false)
		Serialization.write(message, new OutputStreamWriter(dos))
    finalized = true
  }

  def close(): Unit = {
    bzip2.close
    dos.close
    outputStream.close
  }
}

class RegisterThread(hostname: String, port: Int)(implicit ctx: Context)
		extends Thread("RegisterThread") with Log {
  private var deadline = 0L
  private var alive = true
  private var active = true
  private val lock = new Object

  def shutdown() = {
    alive = false
    lock.synchronized {
      lock.notifyAll
    }
    log.info("Shutting down")
    try {
      // well, at least try to de-register
      Https.send[EndpointRegisterOutputMessage, EndpointRegisterInputMessage](
				ctx.config.runner.deregister_url,
        new EndpointRegisterInputMessage(hostname, port),
        true
      )
    } catch {
      case _: Throwable => {
        // Best effort is best effort.
      }
    }
  }

  def acquire(): Boolean = lock.synchronized {
    if (!active) return false
    active = false
    return true
  }

  def release() = lock.synchronized {
    active = true
    extendDeadline
  }

  private def extendDeadline() = {
    deadline = System.currentTimeMillis + 1 * 60 * 1000;
  }

  private def waitUntilDeadline(): Unit = {
    while (alive) {
      val time = System.currentTimeMillis
      if (time >= deadline) return

      try {
        lock.synchronized {
          lock.wait(deadline - time)
        }
      } catch {
        case e: InterruptedException => {}
      }
    }
  }

  override def run(): Unit = {
    while (alive) {
      waitUntilDeadline
      if (!alive) return
      if (active) {
        try {
          Https.send[EndpointRegisterOutputMessage, EndpointRegisterInputMessage](
						ctx.config.runner.register_url,
            new EndpointRegisterInputMessage(hostname, port),
            true
          )
        } catch {
          case e: IOException => {
            log.error(e, "Failed to register")
          }
        }
      }
      extendDeadline
    }
  }
}

object Service extends Object with Log with Using with ContextMixin {
  def lock[T](registerThread: RegisterThread)(success: =>T, failure: =>T): T = {
    if (registerThread.acquire) {
      try {
        success
      } finally {
        registerThread.release
      }
    } else {
      failure
    }
  }

  override def start() = {
    // Get local hostname
    val hostname = serviceCtx.config.runner.hostname

    if (hostname == "") {
      throw new IllegalArgumentException("runner.hostname configuration must be set")
    }

		new File(serviceCtx.config.common.roots.input).mkdirs
		new File(serviceCtx.config.common.roots.compile).mkdirs

    var registerThread: RegisterThread = null

    // And build a runner instance
    val runner = new Runner(
			hostname,
			serviceCtx.config.runner.sandbox match {
				case "null" => NullSandbox
				case _ => Minijail
			}
		)

    // the handler
    val handler = new AbstractHandler() {
      @throws(classOf[IOException])
      @throws(classOf[ServletException])
      override def handle(target: String,
                          baseRequest: Request,
                          request: HttpServletRequest,
                          response: HttpServletResponse) = {
        request.getPathInfo() match {
          case "/run/" => lock[Unit](registerThread) ({
            var token: String = null
            response.setContentType("application/x-omegaup-runstream")
            response.setStatus(HttpServletResponse.SC_OK)

            using (new OmegaUpRunstreamWriter(response.getOutputStream)) { callbackProxy => {
							val message = Logging.debugWrap(ctx => {
								try {
									val req = Serialization.read[RunInputMessage](request.getReader)
									runner.run(req, callbackProxy)(ctx(req.debug))
								} catch {
									case e: Exception => {
										log.error(e, "/run/")
										new RunOutputMessage(status = "error", error = Some(e.getMessage))
									}
								}
							})
							log.info("Returning {}", message)
							if (token != null && ((message.error getOrElse "") != "missing input"))
								runner.removeCompileDir(token)
							callbackProxy.finalize(message)
            }}
          }, {
            response.setContentType("text/json")
            response.setStatus(HttpServletResponse.SC_CONFLICT)
						Serialization.write(
							new RunOutputMessage(status="error",
								error=Some("Resource busy")),
							response.getWriter)
          })
          case _ => {
            response.setContentType("text/json")
            Serialization.write(request.getPathInfo() match {
              case "/compile/" => lock(registerThread) ({
								Logging.debugWrap(ctx => {
									try {
										val req = Serialization.read[CompileInputMessage](request.getReader())
										response.setStatus(HttpServletResponse.SC_OK)
										runner.compile(req)(ctx(req.debug))
									} catch {
										case e: Exception => {
											log.error(e, "/compile/")
											response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
											new CompileOutputMessage(status = "error", error = Some(e.getMessage))
										}
									}
								}).toJson
              }, {
                response.setStatus(HttpServletResponse.SC_CONFLICT)
                new CompileOutputMessage(status="error", error=Some("Resource busy")).toJson
              })
              case "/input/" => lock(registerThread) ({
                try {
                  log.info("/input/")

                  response.setStatus(HttpServletResponse.SC_OK)
                  if(request.getContentType() != "application/x-tar" ||
                     request.getHeader("Content-Disposition") == null) {
                    new InputOutputMessage(
                      status = "error",
                      error = Some("Content-Type must be \"application/x-tar\", " +
                                   "Content-Disposition must be \"attachment\" and a filename " +
                                   "must be specified"
                              )
                    ).toJson
                  } else {
                    val ContentDispositionRegex =
                      "attachment; filename=([a-zA-Z0-9_-][a-zA-Z0-9_.-]*);.*".r

                    val ContentDispositionRegex(inputName) =
                      request.getHeader("Content-Disposition")

                    var tarStream: InputStream = request.getInputStream

                    // Some debugging code to diagnose input transmission problems.
                    if (serviceCtx.config.runner.preserve_tar) {
                      var tarFile = new File(serviceCtx.config.common.roots.input, inputName + ".tar")
                      using (new FileOutputStream(tarFile)) {
                        FileUtil.copy(tarStream, _)
                      }
                      tarStream.close
                      tarStream = new FileInputStream(tarFile)
                    }

                    using (new TarArchiveInputStream(new BufferedInputStream(tarStream))) { tar => {
                      runner.input(inputName, new Iterable[InputEntry] {
                          def iterator = new Iterator[InputEntry] {
                            private var entry: TarArchiveEntry = null
                            private var chunk: ChunkInputStream = null
                            def hasNext = {
                              if (chunk != null) chunk.close
                              entry = tar.getNextTarEntry
                              entry != null
                            }
                            def next = {
                              chunk = new ChunkInputStream(tar, entry.getSize)
                              new InputEntry(entry.getName, chunk, entry.getSize, null)
                            }
                          }
                      })
                    }}.toJson
                  }
                } catch {
                  case e: Exception => {
                    log.error(e, "/input/")
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST)
                    new InputOutputMessage(status = "error", error = Some(e.getMessage)).toJson
                  }
                }
              }, {
                response.setStatus(HttpServletResponse.SC_CONFLICT)
                new InputOutputMessage(status="error", error=Some("Resource busy")).toJson
              })
              case _ => {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND)
                new NullMessage().toJson
              }
            }, response.getWriter())
          }
        }

        baseRequest.setHandled(true)
      }
    };

		val server = new org.eclipse.jetty.server.Server()
		val runnerConnector = (if (serviceCtx.config.ssl.disabled) {
			new org.eclipse.jetty.server.ServerConnector(server)
		} else {
			// boilerplate code for jetty with https support

			val sslContext =
				new org.eclipse.jetty.util.ssl.SslContextFactory(
					serviceCtx.config.ssl.keystore_path
				)
			sslContext.setKeyManagerPassword(serviceCtx.config.ssl.password)
			sslContext.setKeyStorePassword(serviceCtx.config.ssl.keystore_password)
			sslContext.setTrustStore(FileUtil.loadKeyStore(
				serviceCtx.config.ssl.truststore_path,
				serviceCtx.config.ssl.truststore_password
			))
			sslContext.setNeedClientAuth(true)

			new org.eclipse.jetty.server.ServerConnector(server, sslContext)
		})
    runnerConnector.setPort(serviceCtx.config.runner.port)

    server.setConnectors(List(runnerConnector).toArray)
    server.setHandler(handler)

    server.start()

    log.info("Runner {} registering port {}", hostname, runnerConnector.getLocalPort)
    registerThread = new RegisterThread(hostname, runnerConnector.getLocalPort)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() = {
        server.stop
        registerThread.shutdown
      }
    })

    // Send a heartbeat every 5 minutes to register
    registerThread.start

    server.join
    registerThread.join
    log.info("Shut down cleanly")
  }
}

/* vim: set noexpandtab: */
