package com.omegaup

class Config(path: String = "omegaup.conf") {
	private val props = new java.util.Properties(System.getProperties)
	try {
		props.load(new java.io.FileInputStream(path))
	} catch {
		case _: Throwable => {}
	}

	def get[T](propname: String, default: T): T = {
		props.getProperty(propname) match {
			case null => default
			case value: Any => default match {
				case x:String  => value.asInstanceOf[T]
				case x:Int     => value.toInt.asInstanceOf[T]
				case x:Boolean => (value == "true").asInstanceOf[T]
				case _	 => null.asInstanceOf[T]
			}
		}
	}

	def set[T](propname: String, value: T): Unit = {
		props.setProperty(propname, value.toString)
	}
}

/* vim: set noexpandtab: */
