lazy val _version = "0.0.1"

name := "scala-inkbu"
scalaVersion := "2.11.8"
organization := "net.entelijan"
version := _version + "-SNAPSHOT"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
mainClass in assembly := Some("inkbu.Main")
assemblyJarName in assembly := "inkbu-" + _version + ".jar"