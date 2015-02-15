package com.omegaup.grader

import java.sql._
import com.omegaup.data._
import com.omegaup.Database._

import Verdict._
import Validator._
import Language._

object GraderData {
	private def hydrateRun(rs: ResultSet) =
		new Run(
			id = rs.getLong("run_id"),
			guid = rs.getString("guid"),
			user = rs.getString("user_id") match {
				case null => None
				case x: String => Some(new User(
					id = rs.getLong("user_id"),
					username = rs.getString("username")
				))
			},
			language = Language.withName(rs.getString("language")),
			status = Status.withName(rs.getString("status")),
			verdict = Verdict.withName(rs.getString("verdict")),
			time = rs.getTimestamp("time"),
			runtime = rs.getLong("runtime"),
			memory = rs.getLong("memory"),
			submit_delay = rs.getInt("submit_delay"),
			score = rs.getDouble("score"),
			contest_score = rs.getString("contest_score") match {
				case null => None
				case x: String => Some(x.toDouble)
			},
			judged_by = rs.getString("judged_by") match {
				case null => None
				case x: String => Some(x)
			},
			problem = hydrateProblem(rs),
			contest = rs.getLong("contest_id") match {
				case 0 => None
				case x: Long => Some(new Contest(
					id = rs.getLong("contest_id"),
					alias = rs.getString("contest_alias"),
					start_time = rs.getTimestamp("start_time"),
					finish_time = rs.getTimestamp("finish_time"),
					points_decay_factor = rs.getDouble("points_decay_factor"),
					urgent = rs.getInt("urgent") == 1
				))
			}
		)

	private def hydrateProblem(rs: ResultSet) =
		new Problem(
			id = rs.getLong("problem_id"),
			validator = Validator.withName(rs.getString("validator")),
			alias = rs.getString("alias"),
			time_limit = rs.getString("time_limit") match {
				case null => None
				case x: String => Some(x.toLong)
			},
			overall_wall_time_limit = rs.getString("overall_wall_time_limit") match {
				case null => None
				case x: String => Some(x.toLong)
			},
			extra_wall_time = rs.getInt("extra_wall_time").toLong,
			memory_limit = rs.getString("memory_limit") match {
				case null => None
				case x: String => Some(x.toLong)
			},
			output_limit = rs.getString("output_limit") match {
				case null => None
				case x: String => Some(x.toLong)
			},
			stack_limit = rs.getString("stack_limit") match {
				case null => None
				case x: String => Some(x.toLong)
			},
			points = rs.getString("points") match {
				case null => None
				case x: String => Some(x.toDouble)
			},
			slow = rs.getInt("slow") == 1
		)

	def getRun(id: String)(implicit connection: Connection): Option[Run] =
		query("""
			SELECT
				r.*, p.*, u.username, cp.points, c.alias AS contest_alias,
				c.start_time, c.finish_time, c.points_decay_factor, r.submit_delay,
				c.penalty, c.urgent
			FROM
				Runs AS r
			INNER JOIN
				Problems AS p ON
					p.problem_id = r.problem_id
			LEFT JOIN
				Users AS u ON
					u.user_id = r.user_id
			LEFT JOIN
				Contests AS c ON
					c.contest_id = r.contest_id
			LEFT JOIN
				Contest_Problems AS cp ON
					cp.contest_id = r.contest_id AND
					cp.problem_id = r.problem_id
			WHERE
				r.guid = ?;
			""",
			id
		) { hydrateRun }

	def getProblem(id: String)(implicit connection: Connection): Option[Problem] =
		query("""
			SELECT
				p.*, NULL as points
			FROM
				Problems AS p
			WHERE
				p.alias = ?;
			""",
			id
		) { hydrateProblem }

	def getRuns()(implicit connection: Connection): Iterable[Run] =
		queryEach("""
			SELECT
				r.*, p.*, u.username, cp.points, c.alias AS contest_alias,
				c.start_time, c.finish_time, c.points_decay_factor, r.submit_delay,
				c.penalty, c.urgent
			FROM
				Runs AS r
			INNER JOIN
				Problems AS p ON
					p.problem_id = r.problem_id
			LEFT JOIN
				Users AS u ON
					u.user_id = r.user_id
			LEFT JOIN
				Contests AS c ON
					c.contest_id = r.contest_id
			LEFT JOIN
				Contest_Problems AS cp ON
					cp.contest_id = r.contest_id AND
					cp.problem_id = r.problem_id
			"""
		) { hydrateRun }

	def pendingRuns()(implicit connection: Connection): Iterable[Run] =
		queryEach("""
			SELECT
				r.*, p.*, u.username, cp.points, c.alias AS contest_alias,
				c.start_time, c.finish_time, c.points_decay_factor, r.submit_delay,
				c.penalty, c.urgent
			FROM
				Runs AS r
			INNER JOIN
				Problems AS p ON
					p.problem_id = r.problem_id
			INNER JOIN
				Users AS u ON
					u.user_id = r.user_id
			LEFT JOIN
				Contests AS c ON
					c.contest_id = r.contest_id
			LEFT JOIN
				Contest_Problems AS cp ON
					cp.contest_id = r.contest_id AND
					cp.problem_id = r.problem_id
			WHERE
				r.status != 'ready';
			"""
		) { hydrateRun }

	def update(run: Run)(implicit connection: Connection): Run = {
		execute("""
			UPDATE
				Runs
			SET
				status = ?, verdict = ?, runtime = ?, memory = ?, score = ?,
				contest_score = ?, judged_by = ?
			WHERE
				run_id = ?;
			""",
			run.status,
			run.verdict,
			run.runtime,
			run.memory,
			run.score,
			run.contest_score match {
				case None => null
				case Some(x) => x
			},
			run.judged_by,
			run.id
		)
		run
	}

	def insert(run: Run)(implicit connection: Connection): Run = {
		execute(
			"INSERT INTO Runs (user_id, problem_id, contest_id, guid, language, verdict, ip, time) VALUES(?, ?, ?, ?, ?, ?, ?, ?);",
			run.user.map(_.id),
			run.problem.id,
			run.contest.map(_.id),
			run.guid,
			run.language,
			run.verdict,
			run.ip,
			run.time
		)
		run.id = query("SELECT LAST_INSERT_ID()") { rs => rs.getInt(1) }.get
		run
	}
}

/* vim: set noexpandtab: */
