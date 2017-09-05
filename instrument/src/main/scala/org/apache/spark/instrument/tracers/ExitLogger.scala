package org.apache.spark.instrument.tracers

import javassist._
import org.apache.spark.instrument.{MethodInstrumentation, TraceWriter}

case class Exit(code: Int)

object ExitLogger {
  def log(code: Int): Unit = {
    TraceWriter.log(System.currentTimeMillis, Exit(code))
  }
}

class ExitLogger extends MethodInstrumentation {
  override def matches(method: CtBehavior): Boolean = {
    check(method, "java.lang.Shutdown", "exit")
  }
  override def apply(method: CtBehavior): Unit = {
    method.insertBefore(functionCall(this.getClass.getCanonicalName, "log", Seq("$1")))
    //method.insertBefore("{ java.io.FileWriter out = new java.io.FileWriter(\"/tmp/spark-trace/\" + java.util.UUID.randomUUID().toString() + \".tsv\"); out.write(System.currentTimeMillis().toString() + \"\\t\" + \"Exit($1)\\n\"); out.close(); }")
  }
}
