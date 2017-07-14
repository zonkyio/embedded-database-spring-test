create table test.person (
  id         bigint primary key not null,
  first_name varchar(255)       not null,
  surname    varchar(255)       not null
);

insert into test.person (id, first_name, surname) values (1, 'Dave', 'Syer');