package com.omegaup

object Config {
	private val props = new java.util.Properties(System.getProperties)
	load()

	def load(path: String = "omegaup.conf"): Unit = {
		try{
			props.load(new java.io.FileInputStream(path))
		} catch {
			case _: Throwable => {}
		}
	}

	def get[T](propname: String, default: T): T = {
		props.getProperty(propname) match {
			case null => default
			case ans:Any => default match {
				case x:String  => ans.asInstanceOf[T]
				case x:Int     => ans.toInt.asInstanceOf[T]
				case x:Boolean => (ans == "true").asInstanceOf[T]
				case _	 => null.asInstanceOf[T]
			}
		}
	}

	def set[T](propname: String, value: T): Unit = {
		props.setProperty(propname, value.toString)
	}
}
