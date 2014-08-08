package git

import org.joda.time.{Minutes, DateTimeZone, DateTime}
import git.PullRequestType.PullRequestType

/**
 * An object that holds information about the pull request.
 * @param number The number of the pull request.
 * @param author The author name.
 * @param source The source branch name.
 * @param target The target branch name.
 */
case class PullRequest( repository: RepositoryProvider,
                        number: Int,
                        author: String,
                        sha: String,
                        source: String,
                        target: String,
                        var title: Option[String] = None,
                        var createdAt: Option[DateTime] = None,
                        var updatedAt: Option[DateTime] = None,
                        var linesAdded: Option[Long] = None,
                        var linesDeleted: Option[Long] = None,
                        var filesChanged: Option[Long] = None,
                        var commits: Option[Long] = None,
                        var avatar: Option[String] = None,
                        var coreMember: Option[Boolean] = None,
                        var comments: Option[Long] = None,
                        var reviewComments: Option[Long] = None,
                        var labels: Option[List[String]] = None,
                        var milestone: Option[Long] = None,
                        var `type`: Option[PullRequestType] = None,
                        var isMergeable: Option[Boolean] = None,
                        var conflictsWith: Option[List[PullRequest]] = None,
                        var contributedCommits: Option[Int] = None,
                        var acceptedPullRequests: Option[Int] = None,
                        var totalPullRequests: Option[Int] = None,
                        var important: Option[Boolean] = None
                        ) {
  /**
   * @return The total number of added/edited/deleted lines.
   */
  def linesTotal: Long = linesAdded.getOrElse(0L) + linesDeleted.getOrElse(0L)

  def age: Int = createdAt.map(date => Minutes.minutesBetween(date, DateTime.now).getMinutes).getOrElse(0) // minutes

  def createdAtUtc: Option[DateTime] = createdAt.map(date => date.toDateTime(DateTimeZone.UTC))

  def updatedAtUtc: Option[DateTime] = updatedAt.map(date => date.toDateTime(DateTimeZone.UTC))

  def conflictsWithNumbers: Option[List[Int]] = conflictsWith.map(list => list.map(pr => pr.number))

  def hasReviewComments: Option[Boolean] = reviewComments.map(n => n > 0)

  def contributedCommitRatio: Option[Double] = contributedCommits.map(commits => commits.toDouble / repository.commits.toDouble)

  def pullRequestAcceptRatio: Option[Double] = acceptedPullRequests.map(pulls => pulls.toDouble / totalPullRequests.get.toDouble)

  override def toString: String =
    s"#$number: '$source' into '$target'"
}
