package inkbu

import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.io.FileReader
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.PrintWriter
import java.util.Arrays
import java.nio.file.Path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Const {
  val notDefinedStr = "NOT_DEFINED"

}

case class Config(
    baseDir: String,
    buDir: String,
    latestCheck: Date) {
  def complete = !baseDir.contains(Const.notDefinedStr)
}

object Main extends App {
  val debug = args.length > 0 && args(0).contains("-d")

  try {

    var counter = 0L
    var hcounter = 0L

    val sdf = new SimpleDateFormat("yyyy-MM-dd")
    val ch: IConfigHandler = ConfigHandler

    val config = ch.config

    require(config.complete, "Config file %s not ready. Syntax: '<baseDir>;<buDir>;<latestCheck(yyyy-mm-dd)>'" format ch.configFile)
    val bh: IBuHandler = BuHandler(config, debug)

    val baseDirStr = config.baseDir
    val latestCheck: Date = config.latestCheck

    def handleFile(file: File): Unit = {
      val lastModMilli = file.lastModified()
      val lastMod = new Date(lastModMilli)

      val isNew = latestCheck.before(lastMod)

      if (isNew) {
        if (debug) println("%10s %10s %s" format (sdf.format(latestCheck), sdf.format(lastMod), file))
        bh.copy(file)
        hcounter += 1L
      }
      counter += 1L
    }

    def handleDirectory(dir: File, f: File => Unit): Unit = {

      def handleDirectoryFile(file: File): Unit = {
        if (!file.isHidden()) {
          if (file.isFile()) {
            f(file)
          } else if (file.isDirectory()) handleDirectory(file, f)
          else {
            //println("Unknown: %s" format file)
            ()
          }
        } else {
          //println("Hidden: %s" format file)
          ()
        }
      }

      def handleDirectoryFiles(files: List[File]): Unit = {
        files match {
          case Nil => ()
          case file :: rest => {
            handleDirectoryFile(file)
            handleDirectoryFiles(rest)
          }
        }
      }

      val files = dir.listFiles()
      if (files != null) {
        handleDirectoryFiles(files.toList)
      } else {
        ()
      }
    }

    val r = new Runnable {
      def run: Unit = println("handeled %d of %d" format (hcounter, counter))
    }
    val scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(r, 5, 5, TimeUnit.SECONDS);

    val baseDir = new File(baseDirStr)
    require(baseDir.exists(), "Basedirectory '%s' does not exist" format baseDir)
    require(baseDir.isDirectory())

    handleDirectory(baseDir, handleFile)
    println("handeled %d of %d files since %s" format (hcounter, counter, sdf.format(latestCheck)))
    ch.defineCheckDateNow(Some(config))
    scheduler.shutdown()
  } catch {
    case e: Exception => {
      if (debug) throw e
      else println(e.getMessage)
    }
  }

}

trait IBuHandler {
  def copy(f: File): Unit
}

case class BuHandler(config: Config, debug: Boolean) extends IBuHandler {

  val buDir = getCreateDir(config.buDir)

  val baseDir: File = new File(config.baseDir)
  require(baseDir.exists(), "Basedir '%s' does not exist" format baseDir)
  require(baseDir.isDirectory(), "Basedir '%s' is not a directory" format baseDir)

  val baseDirList: List[String] = splitFile(baseDir)
  val buDirList: List[String] = splitFile(buDir)

  def splitFile(file: File): List[String] = {
    def convertFileName(name: String): String =
      if (name.isEmpty()) "/" else name
    
    val parent = file.getParentFile
    if (parent != null) splitFile(file.getParentFile) ::: List(convertFileName(file.getName))
    else List(file.getName)
  }

  def getCreateDir(dirStr: String): File = {
    val dir = new File(dirStr)
    if (!dir.exists()) {
      val created = dir.mkdirs()
      require(created, "directory '%s' could not be created" format dir)
      //println("Created directory '%s'" format dir)
    } else {
      require(dir.isDirectory(), "'%s' is not a directory" format dir)
    }
    dir
  }

  def fileFromList(dir: Option[File], fileList: List[String]): (File, String) = {
    fileList match {
      case Nil         => throw new IllegalStateException("File list must not be empty")
      case name :: Nil => (dir.getOrElse(throw new IllegalStateException("directory path must be defined when fileList is empty")), name)
      case path :: rest =>
        val dir1 = dir match {
          case None => new File(path)
          case Some(x) => new File(x, path)
        }
        fileFromList(Some(dir1), rest)
    }
  }

  def copy(from: File, toDir: File, fileName: String): Unit = {
    require(from.isFile(), "'%s' is no file" format from)
    if (toDir.exists()) {
      require(toDir.isDirectory(), "'%s' is no direcory" format toDir)
    } else {
      val created = toDir.mkdirs()
      require(created, "directory '%s' could not be created" format toDir)
      //println("created directory '%s'" format toDir)
    }
    val fromPath = FileSystems.getDefault.getPath(from.getParentFile.getAbsolutePath, from.getName)
    val toPath = FileSystems.getDefault.getPath(toDir.getAbsolutePath, fileName)
    if (debug)
      println("'%s' -> '%s'" format(fromPath, toPath))
    Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
  }

  def copy(f: File): Unit = {
    val fileList = splitFile(f)
    val relFileList = fileList diff baseDirList
    val toFileList = buDirList ::: relFileList
    val (toDir, fileName) = fileFromList(None, toFileList)
    copy(f, toDir, fileName)
  }
}

trait IConfigHandler {
  def config: Config
  def defineCheckDateNow(config: Option[Config]): Unit
  def configFile: File
}

object ConfigHandler extends IConfigHandler {

  val sdf = new SimpleDateFormat("yyyy-MM-dd")

  def configFile: File = {
    val userHome = getUserHome
    new File(userHome, ".inkbu")
  }

  def getUserHome: File = {
    val userHome = new File(System.getProperty("user.home"))
    if (userHome == null || !userHome.isDirectory())
      throw new IllegalStateException("Jour system provides no user home directory")
    userHome
  }

  def config: Config = {
    val dateFile = configFile
    if (!dateFile.exists()) {
      defineCheckDateNow(None)
      config
    } else {
      val line = new scala.io.BufferedSource(new FileInputStream(dateFile)).mkString
      val data: Array[String] = line.split(";")
      require(data.length == 3, "Data string doues not have 3 data. '%s'" format data.toList.mkString("[", ", ", "]"))
      Config(
        data(0),
        data(1),
        new Date(sdf.parse(data(2)).getTime - (24 * 3600 * 1001)))
    }

  }
  def defineCheckDateNow(config: Option[Config]): Unit = {
    val dateFile = configFile
    val pw = new PrintWriter(dateFile)
    val c = config.getOrElse(defaultConfig)
    pw.print(c.baseDir)
    pw.print(";")
    pw.print(c.buDir)
    pw.print(";")
    pw.print(sdf.format(new Date()))
    pw.close()
  }

  def defaultConfig: Config = {
    Config(
      "baseDir:" + Const.notDefinedStr,
      "buDir:" + Const.notDefinedStr,
      new Date())
  }
}


