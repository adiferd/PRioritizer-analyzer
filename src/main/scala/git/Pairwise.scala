package git

import scala.collection.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Pairwise(pullRequests: List[PullRequest], skipDifferentTargets: Boolean = true) extends PairwiseList {
  /**
   * An ordering for pull requests based on their number.
   */
  implicit val prOrd = Ordering.by[PullRequest, Int](_.number)
  implicit val pairOrd = Ordering.by[PullRequestPair, (PullRequest,PullRequest)](_.tuple)

  val pairs = filterPairs
  val length = pairs.length

  override def get: List[Future[PullRequestPair]] = pairs map { p => Future(p) }

  def unpair = Pairwise.unpair(this)

  private def filterPairs: List[PullRequestPair] = {
    if (skipDifferentTargets)
      getPairs(pullRequests) filter { case PullRequestPair(pr1, pr2, _) => pr1.target == pr2.target }
    else
      getPairs(pullRequests)
  }

  /**
   * Returns a list of distinct paired pull requests.
   * @param pulls A list of pull requests.
   * @return The list of pairs.
   */
  private def getPairs(pulls: List[PullRequest]): List[PullRequestPair] = {
    val singles = pulls.groupBy(p => p.target).filter { case (_, group) => group.length == 1 }.map(_._2(0))

    val pairs = for {
    // Pairwise
      x <- pulls
      y <- pulls
      // Normalize
      if x.number != y.number
      pr1 = if (x.number < y.number) x else y
      pr2 = if (x.number < y.number) y else x
    } yield PullRequestPair(pr1, pr2)

    val pairsWithSingles = pairs ++ singles.map(p => PullRequestPair(p,p))

    // Distinct and sort
    SortedSet(pairsWithSingles: _*).toList
  }
}

object Pairwise {
  implicit val ord = Ordering.by[PullRequest, Int](_.number)

  def pair(pullRequests: List[PullRequest], skipDifferentTargets: Boolean = true) = new Pairwise(pullRequests, skipDifferentTargets)

  def unpair(pairs: Pairwise): List[PullRequest] = unpair(pairs.pairs)

  def unpair(pairs: List[PullRequestPair]): List[PullRequest] = {
    val pulls = distinct(pairs)
    pulls.foreach { pr =>
      val list = pairs filter {
        case PullRequestPair(pr1, pr2, Some(mergeable)) =>
           !mergeable && (pr1 == pr || pr2 == pr)
      } map {
        case PullRequestPair(pr1, pr2, _) =>
          if (pr1 == pr) pr2 else pr1
      }
      pr.conflictsWith = Some(list)
    }
    pulls
  }

  private def distinct(pairs: List[PullRequestPair]): List[PullRequest] = {
    val list = scala.collection.mutable.ListBuffer[PullRequest]()

    pairs.foreach(pair => {
      if (!list.contains(pair.pr1))
        list += pair.pr1
      if (!list.contains(pair.pr2))
        list += pair.pr2
    })
    list.sortBy(p => p).toList
  }
}
