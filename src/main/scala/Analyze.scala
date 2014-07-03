import git._
import org.slf4j.LoggerFactory
import output.JsonWriter
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import utils.{ProgressMonitor, Stopwatch}
import scala.concurrent.ExecutionContext.Implicits.global

object Analyze {
  val timer = new Stopwatch
  val monitor = new ProgressMonitor
  val logger = LoggerFactory.getLogger("Application")

  def main(args: Array[String]): Unit = {
    var loader: Provider = null
    val skipDifferentTargets = Settings.get("pairs.targets.equal").get.toBoolean
    val outputDir = Settings.get("output.directory").get

    try {
      timer.start()
      logger info s"Setup providers..."
      loader = new ProviderLoader
      val prProvider = loader.pullRequestProvider.orNull
      val simplePulls = new ProviderToList(prProvider)
      logger info s"Setup done"
      timer.logLap()

      logger info s"Fetching pull requests..."
      logger info s"Fetching pull request meta data..."
      val fetchGit: Future[Unit] = loader.init()
      val fetchPulls = simplePulls.init()

      // Wait for fetch to complete
      Await.result(fetchGit, Duration.Inf)
      Await.result(fetchPulls, Duration.Inf)

      logger info s"Fetching done"
      logger info s"Got ${simplePulls.length} open pull requests"
      timer.logLap()

      logger info s"Enriching pull request meta data..."
      logger info s"Merging PRs... (${simplePulls.length})"
      val pullDecorator: PullRequestList = loader.getDecorator(simplePulls)
      val decorationOfPulls = pullDecorator.get
      monitor.setTotal(simplePulls.length)
      monitor.incrementWhen(decorationOfPulls)

      // Wait for enrichment to complete
      val pullRequests = Await.result(Future.sequence(decorationOfPulls), Duration.Inf)
      logger info s"Enriching done"
      timer.logLap()

      val simplePairs = new Pairwise(pullRequests, skipDifferentTargets)
      val pairDecorator: PairwiseList = loader.getPairwiseDecorator(simplePairs)

      logger info s"Pairwise merging PRs... (${simplePairs.length})"
      val decorationOfPairs = pairDecorator.get
      monitor.reset()
      monitor.setTotal(simplePairs.length)
      monitor.incrementWhen(decorationOfPairs)

      // Wait for merges to complete
      val pairs = Await.result(Future.sequence(decorationOfPairs), Duration.Inf)
      logger info s"Merging done"
      timer.logLap()

      // Output pull requests
      val unpairedPullRequests = Pairwise.unpair(pairs)
      JsonWriter.writePullRequests(outputDir, loader, unpairedPullRequests)
    } finally {
      if (loader != null)
        loader.dispose()
    }
  }
}
