package com.omegaup

import java.io.IOException
import java.net.HttpURLConnection

import scala.language.experimental.macros

trait Using {
	def using[A, B] (closeable: B) (f: B => A): A = macro UsingMacros.using[A, B]
}

object UsingMacros {
	import scala.reflect.runtime.universe._
	import scala.reflect.macros._
	import blackbox.Context

	def using[A, B: c.WeakTypeTag](c: Context)(closeable: c.Expr[B])(f: c.Expr[B => A]): c.Expr[A] = {
		val using = new UsingMacros[c.type](c)
		val tpe = c.weakTypeOf[B]
		tpe match {
			case _ if tpe <:< c.typeOf[Process] =>
				c.Expr[A](using.process_using(closeable.tree, f.tree))
			case _ if tpe <:< c.typeOf[HttpURLConnection] =>
				c.Expr[A](using.connection_using(closeable.tree, f.tree))
			case _ => c.Expr[A](using.using(closeable.tree, f.tree))
		}
	}

	class UsingMacros[C <: Context](val c: C) {
		import c.universe._

		def using(closeableTree: c.Tree, f: c.Tree): c.Tree = {
			val closeable = TermName(c.freshName)
			q"""
				{
					val $closeable = $closeableTree
					try {
						$f($closeable)
						} finally {
							$closeable.close
						}
				}
			"""
		}

		def process_using(processTree: c.Tree, f: c.Tree): c.Tree = {
			val process = TermName(c.freshName)
			q"""
				{
					val $process = $processTree
					try {
						$f($process)
					} finally {
						if ($process != null) {
							$process.getInputStream.close()
							$process.getOutputStream.close()
							$process.getErrorStream.close()
						}
					}
				}
			"""
		}

		def connection_using(connectionTree: c.Tree, f: c.Tree): c.Tree = {
			val connection = TermName(c.freshName)
			q"""
				{
					val $connection = $connectionTree
					// This tries to leverage HttpURLConnection's Keep-Alive facilities.
					try {
						$f($connection)
					} catch {
						case e: IOException => {
							try {
								val err = $connection.getErrorStream
								val buf = Array.ofDim[Byte](1024)
								var ret: Int = 0
								while ({ ret = err.read(buf); ret > 0}){}
								err.close
							} catch {
								case _: Throwable => {}
							}
							throw e
						}
					} finally {
						try {
							$connection.getInputStream.close
						} catch {
							case _: Throwable => {}
						}
					}
				}
			"""
		}
	}
}

/* vim: set noexpandtab: */
