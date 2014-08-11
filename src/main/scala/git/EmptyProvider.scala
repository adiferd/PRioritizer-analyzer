package git

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class EmptyProvider extends Provider {
  override val repositoryProvider: Option[RepositoryProvider] = Some(new EmptyRepositoryProvider)
  override val pullRequestProvider: Option[PullRequestProvider] = Some(new EmptyPullRequestProvider)
  override def getDecorator(list: PullRequestList): PullRequestList = list
  override def getPairwiseDecorator(list: PairwiseList): PairwiseList = list
}

class EmptyRepositoryProvider extends RepositoryProvider {
  val commits = 0L
}

class EmptyPullRequestProvider extends PullRequestProvider {
  val get = Future(List[PullRequest]())

  val source = ""

  val owner = ""

  val repository = ""

  val ssh = ""

  val https = ""

  val remotePullHeads = ""

  val remoteHeads = ""
}