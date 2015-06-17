CREATE TABLE IF NOT EXISTS `Contests` (
  `contest_id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'El identificador unico para cada concurso',
  `title` varchar(256) NOT NULL COMMENT 'El titulo que aparecera en cada concurso',
  `description` tinytext NOT NULL COMMENT 'Una breve descripcion de cada concurso.',
  `start_time` timestamp NOT NULL DEFAULT '2000-01-01 00:00:00' COMMENT 'Hora de inicio de este concurso',
  `finish_time` timestamp NOT NULL DEFAULT '2000-01-01 00:00:00' COMMENT 'Hora de finalizacion de este concurso',
  `window_length` int(11) DEFAULT NULL COMMENT 'Indica el tiempo que tiene el usuario para envíar solución, si es NULL entonces será durante todo el tiempo del concurso',
  `director_id` int(11) NOT NULL COMMENT 'el userID del usuario que creo este concurso',
  `rerun_id` int(11) NOT NULL COMMENT 'Este campo es para las repeticiones de algún concurso',
  `public` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'False implica concurso cerrado, ver la tabla ConcursantesConcurso',
  `alias` varchar(20) NOT NULL COMMENT 'Almacenará el token necesario para acceder al concurso',
  `scoreboard` int(11) NOT NULL DEFAULT '1' COMMENT 'Entero del 0 al 100, indicando el porcentaje de tiempo que el scoreboard será visible',
  `points_decay_factor` double NOT NULL DEFAULT 0 COMMENT 'Valor de 0 a 1, indicando la tasa de decaimiento de los puntos',
  `partial_score` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Verdadero si el usuario recibirá puntaje parcial para problemas no resueltos en todos los casos',
  `submissions_gap` int(11) NOT NULL DEFAULT '1' COMMENT 'Tiempo mínimo en segundos que debe de esperar un usuario despues de realizar un envío para hacer otro',
  `feedback` varchar(10) NOT NULL,
  `penalty` int(11) NOT NULL DEFAULT '1' COMMENT 'Entero indicando el número de minutos con que se penaliza por recibir un no-accepted',
  `penalty_type` varchar(16) NOT NULL COMMENT 'Indica la política de cálculo de penalty: minutos desde que inició el concurso, minutos desde que se abrió el problema, o tiempo de ejecución (en milisegundos).',
  `penalty_calc_policy` varchar(3) DEFAULT 'sum' NOT NULL COMMENT 'Indica como afecta el penalty al score.',
  `urgent` tinyint(1) DEFAULT '0' NOT NULL COMMENT 'Indica si el concurso es de alta prioridad y requiere mejor QoS.',
  PRIMARY KEY (`contest_id`)
);

CREATE TABLE IF NOT EXISTS `Contest_Problems` (
  `contest_id` int(11) NOT NULL,
  `problem_id` int(11) NOT NULL,
  `points` double NOT NULL DEFAULT '1',
  PRIMARY KEY (`contest_id`,`problem_id`)
);

CREATE TABLE IF NOT EXISTS `Contest_Problem_Opened` (
  `contest_id` int(11) NOT NULL,
  `problem_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `open_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`contest_id`,`problem_id`,`user_id`)
);

CREATE TABLE IF NOT EXISTS `Problems` (
  `problem_id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(256) NOT NULL,
  `alias` varchar(10) DEFAULT NULL,
  `validator` varchar(15) NOT NULL DEFAULT 'token-numeric',
  `time_limit` int(11) DEFAULT '3000',
  `validator_time_limit` int(11) DEFAULT '3000',
  `overall_wall_time_limit` int(11) NOT NULL DEFAULT '120000',
  `extra_wall_time` int(11) NOT NULL DEFAULT '0',
  `memory_limit` int(11) DEFAULT '64',
  `output_limit` int(11) NOT NULL DEFAULT '10240',
  `stack_limit` int(11) NOT NULL DEFAULT '10485760',
  `creation_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `slow` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`problem_id`)
);

CREATE TABLE IF NOT EXISTS `Runs` (
  `run_id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NULL DEFAULT NULL,
  `problem_id` int(11) NOT NULL,
  `contest_id` int(11) DEFAULT NULL,
  `guid` char(32) NOT NULL,
  `language` varchar(5) NOT NULL,
  `status` varchar(10) NOT NULL DEFAULT 'new',
  `verdict` varchar(5) NOT NULL,
  `runtime` int(11) NOT NULL DEFAULT '0',
  `penalty` int(11) NOT NULL DEFAULT '0',
  `memory` int(11) NOT NULL DEFAULT '0',
  `score` double NOT NULL DEFAULT '0',
  `contest_score` double NULL DEFAULT NULL,
  `ip` char(15) NOT NULL,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `submit_delay` int(11) NOT NULL DEFAULT '0',
  `test` tinyint(1) NOT NULL DEFAULT '0',
  `judged_by` char(32) NULL DEFAULT NULL,
  PRIMARY KEY (`run_id`)
);

CREATE TABLE IF NOT EXISTS `Users` (
  `user_id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` char(32) DEFAULT NULL,
  `email` varchar(256) DEFAULT NULL,
  `name` varchar(256) DEFAULT NULL,
  `solved` int(11) NOT NULL DEFAULT '0',
  `submissions` int(11) NOT NULL DEFAULT '0',
  `country_id` char(3) DEFAULT NULL,
  `state_id` int(11) DEFAULT NULL,
  `school_id` int(11) DEFAULT NULL,
  `scholar_degree` varchar(64) DEFAULT NULL,
  `graduation_date` date DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `last_access` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
);
