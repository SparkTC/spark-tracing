package org.apache.spark.instrument

import org.apache.spark.rdd.RDD

// TODO Either carefully document tree extractions OR convert extractions to well-named functions
// TODO Like: def getFunctionName(ev: EventTree): String = ev(3)(0)

class Axes(resolve: ServiceMap) extends OutputBlock {
  val name: String = "axes"
  val columns: Seq[(String, ColType)] = Seq("name" -> Str)
  def data: Iterable[Seq[Any]] = resolve.filteredServices.map(row => Seq(row.id))
}

class RPCs(events: RDD[EventTree], resolve: ServiceMap) extends OutputBlock {
  val name: String = "rpcs"
  val columns: Seq[(String, ColType)] = Seq("time" -> Time, "origin" -> Str, "destination" -> Str, "content" -> Str)
  def data: Iterable[Seq[Any]] = events.filter(_(3)(0).is("RPC"))
    .filter(row => resolve.filterRPC(row(3)(1).get.get, row(3)(2).get.get))
    .map { row =>
      val ev = row(3)
      Seq(row(2).get.get, resolve.service(ev(1).get.get).id, resolve.service(ev(2).get.get).id, ev(3).toString)
    }.collect
}

class Events(events: RDD[EventTree], resolve: ServiceMap, format: Map[String, FormatSpec]) extends OutputBlock {
  val name: String = "events"
  val columns: Seq[(String, ColType)] = Seq("time" -> Time, "location" -> Str, "content" -> Str)
  private val nonEvents: Set[String] = Set("SpanStart", "SpanEnd", "RPC", "Service")
  def data: Iterable[Seq[Any]] = events.filter(row => row(3)(0).get.exists(!nonEvents.contains(_))).flatMap(row =>
    resolve.mainService(row(1).get.get).map(service => Seq(row(2).get.get, service, Transforms.fmtEvent(row(3), format)))
  ).collect
}

class Spans(events: RDD[EventTree], resolve: ServiceMap, format: Map[String, FormatSpec]) extends OutputBlock {
  val name: String = "spans"
  val columns: Seq[(String, ColType)] = Seq("start" -> Time, "end" -> Time, "location" -> Str, "content" -> Str)
  def data: Iterable[Seq[Any]] = {
    val starts = events.filter(_(3)(0).is("SpanStart")).map(row => (row(3)(1).get.get, (row(1).get.get, row(2).get.get, row(3)(2))))
    val ends = events.filter(_(3)(0).is("SpanEnd")).map(row => (row(3)(1).get.get, row(2).get.get))
    val all = starts.join(ends).map(_._2) // RDD of ((JVM ID, start time, event), end time)
    all.flatMap(span =>
      resolve.mainService(span._1._1).map(service => Seq(span._1._2, span._2, service, Transforms.fmtEvent(span._1._3, format)))
    ).collect
  }
}

class TimeRange(events: RDD[EventTree], resolve: ServiceMap) extends OutputBlock {
  val name: String = "timerange"
  val columns: Seq[(String, ColType)] = Seq("time" -> Time)
  def data: Iterable[Seq[Any]] = {
    // This filtering doesn't take RPCs into account, but it shouldn't matter because traces should always start and end with events or spans.
    val times = events.filter(row => resolve.mainService(row(1).get.get).isDefined).map(_(2).get.get.toLong)
    Seq(Seq(times.min), Seq(times.max))
  }
}