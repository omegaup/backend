package com.omegaup

import java.io._
import java.net._
import scala.xml._
import scala.collection.mutable.TreeSet

object Database extends Object with Log with Using {
	def bmap[T](test: => Boolean)(block: => T): List[T] = {
		val ret = new scala.collection.mutable.ListBuffer[T]
		while(test) ret += block
		ret.toList
	}

	import java.sql._
	
	def build(query: String, params: Any*): String = {
		params.length match {
			case 0 => query
			case _ => {
				var pqi = 0
				var qi = -1
				var pi = 0
				val ans = new StringBuilder
				
				while( {qi = query.indexOf('?', pqi); qi != -1} ) {
					ans.append(query.substring(pqi, qi))
					
					ans.append(params(pi) match {
						case null => "null"
						case None => "null"
						case Some(x) => x match {
							case y: Int => y.toString
							case y: Long => y.toString
							case y: Float => y.toString
							case y: Double => y.toString
							case y: Boolean => if (y) "1" else "0"
							case y => {
								"'" +
								y.toString.replace("\\", "\\\\").replace("'", "''") +
								"'"
							}
						}
						case x: Int => x.toString
						case x: Long => x.toString
						case x: Float => x.toString
						case x: Double => x.toString
						case x: Boolean => if (x) "1" else "0"
						case x => {
							"'" +
							x.toString.replace("\\", "\\\\").replace("'", "''") +
							"'"
						}
					})
					
					pqi = qi + 1
					pi += 1
				}
				
				if(pqi < query.length)
					ans.append(query.substring(pqi))
				
				ans.toString
			}
		}
	}

	/** Executes the SQL and processes the result set using the specified function. */
	def query[B](sql: String, params: Any*)(process: ResultSet => B)(implicit connection: Connection): Option[B] = {
		val q = build(sql, params : _*)
		trace(q)
		try {
			susing (connection.createStatement) { statement =>
				rusing (statement.executeQuery(q)) { results =>
					results.next match {
						case true => Some(process(results))
						case false => None
					}
				}
			}
		} catch {
			case e: Exception => {
				susing (connection.createStatement) { statement =>
					rusing (statement.executeQuery(q)) { results =>
						results.next match {
							case true => Some(process(results))
							case false => None
						}
					}
				}
			}
		}
	}
	
	def execute(sql: String, params: Any*)(implicit connection: Connection): Unit = {
		val q = build(sql, params : _*)
		trace(q)
		try {
			susing (connection.createStatement) { statement =>
				statement.execute(q)
			}
		} catch {
			case e: Exception => {
				susing (connection.createStatement) { statement =>
					statement.execute(q)
				}
			}
		}
	}

	/** Executes the SQL and uses the process function to convert each row into a T. */
	def queryEach[T](sql: String, params: Any*)(process: ResultSet => T)(implicit connection: Connection): Iterable[T] = {
		val q = build(sql, params : _*)
		trace(q)
		val ret = new scala.collection.mutable.ListBuffer[T]
		susing (connection.createStatement) { statement =>
			rusing (statement.executeQuery(q)) { results =>
				while (results.next) {
					ret += process(results)
				}
			}
		}
		ret
	}
}

class XmlWalker(xml: String) {
	val root = XML.loadString(xml)

	def get(path: String): String = {
		var node: NodeSeq = root
		
		for (element <- path.split("\\.")) {
			if (element.contains("=")) {
				val search = element.split("=")
				node = node.filter(x => (x \\ search(0)).text == search(1))
			} else {
				node = node \\ element
			}
		}
		
		return node.text.trim
	}
}

class GitException(message: String) extends RuntimeException(message) {}

class Git(repository: File) extends Object with Using {
  val runtime = Runtime.getRuntime
  val env = List[String]()

  def init() = {
    val params = List("/usr/bin/git", "init", "-q")

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      val error = new StringBuilder

      using (new BufferedReader(new InputStreamReader(p.getErrorStream))) { reader =>
        error.append(reader.readLine)
        error.append('\n')
      }

      if (p.waitFor != 0) {
        throw new GitException(error.toString)
      }
    }}
  }

  def clean() = {
    val params = List("/usr/bin/git", "rm", "-rf", ".")

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      val error = new StringBuilder

      using (new BufferedReader(new InputStreamReader(p.getErrorStream))) { reader =>
        error.append(reader.readLine)
        error.append('\n')
      }

      if (p.waitFor != 0) {
        throw new GitException(error.toString)
      }
    }}
  }

  def commit(username: String, message: String) = {
    val gitAttributes = new File(repository, ".gitattributes")
    if (!gitAttributes.exists) {
      FileUtil.write(gitAttributes,
        "cases/in/* -diff -delta -merge -text -crlf\n" +
				"cases/out/* -diff -delta -merge -text -crlf")
    }

    add(".")

    if (status.length > 0) {
      config("user.email", s"$username@omegaup")
      config("user.name", username)
      config("push.default", "matching")
      val params = List("/usr/bin/git", "commit", "-m", message)

      pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
        val error = new StringBuilder

        using (new BufferedReader(new InputStreamReader(p.getErrorStream))) { reader =>
          error.append(reader.readLine)
          error.append('\n')
        }

        if (p.waitFor != 0) {
          throw new GitException(error.toString)
        }
      }}
    }
  }

  def config(name: String, value: String) = {
    val params = List("/usr/bin/git", "config", name, value)

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      val error = new StringBuilder

      using (new BufferedReader(new InputStreamReader(p.getErrorStream))) { reader =>
        error.append(reader.readLine)
        error.append('\n')
      }

      if (p.waitFor != 0) {
        throw new GitException(error.toString)
      }
    }}
  }

  def add(path: String) = {
    val params = List("/usr/bin/git", "add", ".")

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      val error = new StringBuilder

      using (new BufferedReader(new InputStreamReader(p.getErrorStream))) { reader =>
        error.append(reader.readLine)
        error.append('\n')
      }

      if (p.waitFor != 0) {
        throw new GitException(error.toString)
      }
    }}
  }

  def status(): String = {
    val params = List("/usr/bin/git", "status", "-s", "--porcelain")

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      val output = new StringBuilder

      var first = true
      using (new BufferedReader(new InputStreamReader(p.getInputStream))) { reader =>
        if (!first) {
          output.append('\n')
        } else {
          first = false
        }
        output.append(reader.readLine)
      }

      output.toString
    }}
  }

  def revParse(commitish: String) = {
    val params = List("/usr/bin/git", "rev-parse", commitish)

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      using (new BufferedReader(new InputStreamReader(p.getInputStream))) { reader =>
        reader.readLine
      }
    }}
  }

  def getTreeHash(directory: String) = {
    val params = List("/usr/bin/git", "ls-tree", "HEAD", "-d", directory)

    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      using (new BufferedReader(new InputStreamReader(p.getInputStream))) { reader =>
        reader.readLine.split("\\s")(2)
      }
    }}
  }

  def getTreeEntries(treeish: String): Iterable[(String, String)] = {
    val params = List("/usr/bin/git", "ls-tree", treeish)
    val treeHashes = new TreeSet[(String, String)]

    // Get the files in the directory from git.
    pusing (runtime.exec(params.toArray, env.toArray, repository)) { p => {
      var line: String = null
      using (new BufferedReader(new InputStreamReader(p.getInputStream))) { reader =>{
        while ({ line = reader.readLine ; line != null } ){
          var tokens = line.split("\t")
          val name = tokens(1)
          tokens = tokens(0).split(" ")
          treeHashes += ((name, tokens(2)))
        }
      }}
    }}

    treeHashes
  }
}
