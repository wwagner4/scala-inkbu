# scala-inkbu
incremental backup tool

Configuration file: {user.home}/.inkbu. This file is createdt when you call inkbu the first time.

Format configuratin File: <base-dir>;<bu-dir>;<latest-check-time>
* base-dir: The root directory from where your files should be copied. e.g. 'c:\Users\ww\Documents'
* bu-dir: The root directory where your files should be copied to. e.g. 'f:\bu\Documents'
* latest-check-time: Timestam since when files where saved. The next run only files with modification date > latest-check-time are copied. Format: yyyy-mm-dd. This field is automatically set to the current date after each run uf inkbu

Run inkbu: java -jar inkbu-x.x.x.jar

Build of the inkbu jar
* checkout inkbu
* call 'sbt assembly'  

