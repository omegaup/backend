INSERT INTO Contests(title, description, start_time, finish_time, window_length, director_id, rerun_id, public, alias, scoreboard, partial_score, submissions_gap, feedback, penalty, penalty_time_start) VALUES ('ConTest', 'A test contest', '2000-01-01 00:00:00', '2000-01-01 06:00:00', NULL, 1, 0, 1, 'test', 80, 1, 0, 'yes', 20, 'contest');

INSERT INTO Users(`user_id`, `username`) VALUES (1, 'user');

INSERT INTO Problems(`problem_id`, `title`, `alias`, `validator`, `time_limit`, `overall_wall_time_limit`, `memory_limit`, `creation_date`) VALUES (1, 'Hello, World!', 'HELLO', 'token-caseless', 3000, 30000, 65536, '2000-01-01 00:00:00');

INSERT INTO Problems(`problem_id`, `title`, `alias`, `validator`, `time_limit`, `overall_wall_time_limit`, `memory_limit`, `creation_date`) VALUES (2, 'Hello, World!', 'HELLO2', 'token-caseless', 3000, 30000, 65536, '2000-01-01 00:00:00');

INSERT INTO Problems(`problem_id`, `title`, `alias`, `validator`, `time_limit`, `overall_wall_time_limit`, `memory_limit`, `creation_date`) VALUES (3, 'Hello, World!', 'HELLO3', 'token-caseless', 3000, 30000, 65536, '2000-01-01 00:00:00');

INSERT INTO Problems(`problem_id`, `title`, `alias`, `validator`, `time_limit`, `overall_wall_time_limit`, `memory_limit`, `creation_date`) VALUES (4, 'Hello, World!', 'HELLO4', 'custom', 3000, 30000, 65536, '2000-01-01 00:00:00');

INSERT INTO Problems(`problem_id`, `title`, `alias`, `validator`, `time_limit`, `overall_wall_time_limit`, `memory_limit`, `creation_date`) VALUES (5, 'Hello, World!', 'HELLO5', 'token-caseless', 3000, 30000, 65536, '2000-01-01 00:00:00');

INSERT INTO Problems(`problem_id`, `title`, `alias`, `validator`, `time_limit`, `overall_wall_time_limit`, `memory_limit`, `creation_date`) VALUES (6, 'Hello, Karel!', 'KAREL', 'token-caseless', 3000, 30000, 65536, '2000-01-01 00:00:00');

INSERT INTO Contest_Problems(contest_id, problem_id, points) VALUES(1, 1, 100);
INSERT INTO Contest_Problems(contest_id, problem_id, points) VALUES(1, 2, 100);
INSERT INTO Contest_Problems(contest_id, problem_id, points) VALUES(1, 3, 100);

INSERT INTO Contest_Problem_Opened(contest_id, problem_id, user_id, open_time) VALUES(1, 1, 1, '2000-01-01 00:01:00');
INSERT INTO Contest_Problem_Opened(contest_id, problem_id, user_id, open_time) VALUES(1, 2, 1, '2000-01-01 00:01:00');
INSERT INTO Contest_Problem_Opened(contest_id, problem_id, user_id, open_time) VALUES(1, 3, 1, '2000-01-01 00:01:00');
