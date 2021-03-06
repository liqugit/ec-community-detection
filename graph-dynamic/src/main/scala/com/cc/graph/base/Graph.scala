package com.cc.graph.base

import java.util.concurrent.locks.ReentrantLock
import scala.io.Source
import org.graphstream.graph.Node
import com.cc.graph.Conf
import com.cc.graph.util.LockUtil.withLock
import org.graphstream.graph.implementations.MultiGraph
import org.graphstream.graph.implementations.SingleGraph
import java.awt.Color
import scala.collection.mutable.HashSet
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.collection.concurrent.TrieMap

trait Graph {

  // set graph renderer
  System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

  private[base] var edges: Set[Edge]
  private[base] var vertexes: Set[Vertex]
  private[base] val displayGraph: org.graphstream.graph.Graph
  override def toString(): String = {
    s"""
    |vertexs: ${vertexes.toList.sortBy(_.id).mkString(",")}
    |edges: ${edges.toList.sortBy(_.side1.id).mkString(",")}
    """.stripMargin
  }

  def display = {
    displayGraph.display
  }

  /**
   * display the community by the giving communities structure<br>
   * List[Set[String]] the element Set[String] is a set of nodes of the community
   */
  def displayCommunity(communities: Graph.Communities) = {
    import scala.collection.JavaConversions._
    displayGraph.getEdgeSet[org.graphstream.graph.Edge]().foreach(_.addAttribute("ui.style", "fill-color: rgba(0,0,0,128);"))
    displayGraph.getNodeSet[org.graphstream.graph.Node]().foreach(_.addAttribute("ui.style", "fill-color: white;"))
    val colors = HashSet[Tuple3[Int, Int, Int]]()
    val random = ThreadLocalRandom.current()
    communities.foreach {
      comm =>
        {
          var color: (Int, Int, Int) = null
          do { color = (random.nextInt(256), random.nextInt(256), random.nextInt(256)) } while (colors.contains(color))
          colors += color
          val colorString = s"rgb(${color._1},${color._2},${color._3})"
          comm.foreach { s => displayGraph.getNode[org.graphstream.graph.Node](s).addAttribute("ui.style", s"fill-color: $colorString;") }
          for {
            e1 <- comm
            e2 <- comm
            if e1 < e2
          } {
            if (edges.contains(Edge.cons(e1, e2))) {
              displayGraph.getEdge[org.graphstream.graph.Edge](e1 + "edges" + e2).addAttribute("ui.style", s"fill-color: $colorString;")
            }
          }
        }
    }
    display
  }

  def vertexIds: Set[String] = {
    vertexes.map(_.id)
  }

  def getEdges = edges
  def getVertexes = vertexes

  /**
   *  get the neighbor node of the given node
   */
  def getNeighborVertexes(id: String): Set[String] = {
    getNeighborVertexes(Vertex(id))
  }

  /**
   *  get the neighbor node of the given node
   */
  def getNeighborVertexes(vertex: Vertex): Set[String] = {
    if (!vertexes(vertex)) {
      Set.empty
    } else {
      import scala.collection.JavaConversions._
      val node = displayGraph.getNode[Node](vertex.id)
      node.getNeighborNodeIterator[Node]().map(_.getId).toSet
    }
  }
}

object Graph {

  type Communities = List[Set[String]]

  def load(fileName: String, graphName: String = Conf.projectName, seperator: String = ","): Graph = {
    val source = Source.fromFile(fileName)
    try {
      val g = source.getLines().toStream
        .map(_.trim())
        .filter(_.length() > 0)
        .map(_.split(seperator).filter(_.size > 0))
        .filter(_.size >= 2)
        .foldLeft(MutableGraph(graphName)) { (r, e) => r.addEdge(Edge.cons(Vertex(e(0).trim()), Vertex(e(1).trim()))) }
      ImmutableGraph.from(g)
    } finally { source.close() }
  }

}

case class Vertex(id: String) {
  override def toString: String = id
}

case class Edge private (side1: Vertex, side2: Vertex) {
  /**
   * make sure that the side1 id is smaller than side2 id
   */
  require(side1.id < side2.id)
  override def toString: String = side1 + "-" + side2

  def containsNode(node: Vertex): Boolean = {
    containsNode(node.id)
  }

  def containsNode(id: String): Boolean = {
    id == side1.id || id == side2.id
  }
}

object Edge {
  def cons(s1: Vertex, s2: Vertex): Edge = {
    if (s1.id == s2.id) {
      throw new IllegalArgumentException("s1 should not be equal to s2")
    }
    if (s1.id > s2.id) {
      Edge(s2, s1)
    } else {
      Edge(s1, s2)
    }
  }

  def cons(id1: String, id2: String): Edge = {
    cons(Vertex(id1), Vertex(id2))
  }
}

/**
 * mutable graph data structure<br/>
 * not able to use outside
 */
private class MutableGraph private (
  override private[base] var edges: Set[Edge],
  override private[base] var vertexes: Set[Vertex],
  override private[base] val displayGraph: org.graphstream.graph.Graph) extends Graph {

  private implicit val _lock = new ReentrantLock

  /**
   * remove the vertex and the related edges
   * @param vertex
   *  the vertex to remove
   */
  def removeVertex(vertex: Vertex): this.type = {
    withLock {
      if (vertexes.contains(vertex)) {
        displayGraph.removeNode(vertex.id)
        vertexes = vertexes - vertex
        edges = edges.filterNot { e => e.side1 == vertex || e.side2 == vertex }
      }
    }
    this
  }

  /**
   * remove the edge (but not effect the related vertex)
   * @param edge
   *  the edge to remove
   *
   */
  def removeEdge(edge: Edge): this.type = {
    withLock {
      if (edges.contains(edge)) {
        displayGraph.removeEdge(displayGraph.getNode[Node](edge.side1.id), displayGraph.getNode[Node](edge.side2.id))
        edges = edges - edge
      }
    }
    this
  }

  def addVertex(vertex: Vertex): this.type = {
    withLock {
      if (!vertexes.contains(vertex)) {
        vertexes = vertexes + vertex
        val node = displayGraph.addNode[Node](vertex.id)
        node.addAttribute("ui.label", node.getId)
        node.addAttribute("ui.style", "fill-color: white;");
      }
    }
    this
  }

  def addEdge(edge: Edge): this.type = {
    withLock {
      if (!edges.contains(edge)) {
        val v1 = edge.side1
        val v2 = edge.side2
        addVertex(v1)
        addVertex(v2)
        edges = edges + edge
        displayGraph.addEdge(v1.id + "edges" + v2.id, v1.id, v2.id)
      }
    }
    this
  }
}

private object MutableGraph {
  private[base] def apply(name: String): MutableGraph = {
    val graph = new MultiGraph(name)
    graph.addAttribute("ui.stylesheet", s"url('${MutableGraph.getClass.getResource(".").toString()}stylesheet.css')")
    graph.addAttribute("ui.quality");
    graph.addAttribute("ui.antialias");
    new MutableGraph(Set[Edge](), Set[Vertex](), graph)
  }

  private[base] def from(g: Graph): MutableGraph = {
    new MutableGraph(g.edges, g.vertexes, g.displayGraph)
  }
}

/**
 * Immutable graph structure
 * 不可变图(节点和边已经固定 无法新增 删除,但display可能改变)
 *
 */
class ImmutableGraph private (
  override val edges: Set[Edge],
  override val vertexes: Set[Vertex],
  override private[base] val displayGraph: org.graphstream.graph.Graph) extends Graph {

  override private[base] def edges_=(x$1: Set[Edge]): Unit = ???
  override private[base] def vertexes_=(x$1: Set[Vertex]): Unit = ???

  private val neighborCache = TrieMap[String, Set[String]]()

  override def getNeighborVertexes(vertex: Vertex) = {
    neighborCache.getOrElseUpdate(vertex.id, super.getNeighborVertexes(vertex))
  }
}

object ImmutableGraph {
  private lazy val empty = new ImmutableGraph(Set[Edge](), Set[Vertex](), new SingleGraph(Conf.projectName))
  /**
   * make a new empty immutableGraph
   * 返回一个空的不可变图
   */
  def apply(): ImmutableGraph = {
    empty
  }

  /**
   * convert graph g to an immuatble graph
   * 将传入的图转变为不可变图
   */
  def from(g: Graph): ImmutableGraph = {
    new ImmutableGraph(g.edges, g.vertexes, g.displayGraph)
  }
}

