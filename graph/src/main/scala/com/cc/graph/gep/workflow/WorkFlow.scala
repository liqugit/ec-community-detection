package com.cc.graph.gep.workflow

import java.util.concurrent.{ ThreadLocalRandom => TLRandom }

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

import com.cc.graph.Conf
import com.cc.graph.algorithm.Modularity
import com.cc.graph.base.Graph
import com.cc.graph.gep.Chromosome
import com.cc.graph.gep.Gene
import com.cc.graph.gep.Population

class WorkFlow {

  selection: SelectionStrategy =>

  import Conf.Gep._

  type History = List[Population]
  type Result = (Population, History)

  /**
   * run the init population<br/>
   * the related props are from application.conf
   */
  def run(graph: Graph): Result = {
    val initPopulation = Population.generate(graph, populationSize)
    innerRun((initPopulation, List(initPopulation)), graph, generationNum)
  }

  @tailrec
  private def innerRun(lastResult: Result, graph: Graph, maxGenerationNum: Int): Result = {
    val lastPop = lastResult._1
    println(lastPop.generationNum + "/" + maxGenerationNum)
    if (lastPop.generationNum >= maxGenerationNum) {
      lastResult
    } else {
      // remain one positive for the best
      val choosedChroms = selection.choose(lastPop.chromosomes, graph, lastPop.chromosomes.size - 1)
      val mutatedChroms = for (chrom <- choosedChroms.selected) yield {
        operateChromosome(chrom, graph)
      }
      val theRemainedOne = choosedChroms.best.getOrElse(Chromosome.generate(graph))
      val newPopulation = Population(mutatedChroms :+ theRemainedOne, lastPop.generationNum + 1)
      innerRun((newPopulation, newPopulation :: lastResult._2), graph, maxGenerationNum)
    }
  }

  private def operateChromosome(chrom: Chromosome, graph: Graph): Chromosome = {
    val random = TLRandom.current()
    val ls = ListBuffer[Gene]()
    ls ++= chrom.genes
    if (random.nextDouble() <= geneMove) {
      doGeneMove(ls)
    }
    if (random.nextDouble() <= geneExchange) {
      doGeneExchange(ls)
    }
    if (random.nextDouble() <= geneMerge) {
      doGeneMerge(ls, graph)
    }
    if (random.nextDouble() <= geneSplitoff) {
      doGeneSplitOff(ls)
    }
    Chromosome(ls: _*)
  }

  private def doGeneMove(genes: ListBuffer[Gene]): Unit = {
    val random = TLRandom.current()
    if (genes.size > 1) {
      val g1 = genes.remove(random.nextInt(genes.size))
      val g2 = genes.remove(random.nextInt(genes.size))
      val moved = Gene.move(g1, g2)
      genes ++= moved
    }
  }

  private def doGeneExchange(genes: ListBuffer[Gene]): Unit = {
    val random = TLRandom.current()
    if (genes.size > 1) {
      val g1 = genes.remove(random.nextInt(genes.size))
      val g2 = genes.remove(random.nextInt(genes.size))
      genes ++= Gene.exchange(g1, g2)
    }
  }

  private def doGeneSplitOff(genes: ListBuffer[Gene]): Unit = {
    val geneOfMaxSize = genes.reduce((g1, g2) => if (g1.size > g2.size) g1 else g2)
    genes -= geneOfMaxSize ++= Gene.splitoff(geneOfMaxSize)
  }

  private def doGeneMerge(genes: ListBuffer[Gene], graph: Graph): Unit = {
    val random = TLRandom.current()
    val chrom = Chromosome(genes: _*)
    genes.clear()
    genes ++= selection.choose(Combination(chrom), graph, 0).best.getOrElse(chrom).genes
  }
}

object WorkFlow extends App {
  val workFlow = new WorkFlow with ModularitySelection
  val graph = Graph.load("src/main/resources/test.txt")
  val result = workFlow.run(graph)
  val pop = result._1
  val chromWithModularities = pop.chromosomes zip pop.chromosomes.map(c => Modularity.compute(c.toCommunityStyle, graph).sum)
  val sorted = chromWithModularities.sortBy(-1 * _._2)
  val best = sorted.map(_._1).apply(0)
  println(best)
  println(sorted.map(_._2))
  graph.displayCommunity(best.toCommunityStyle)
}