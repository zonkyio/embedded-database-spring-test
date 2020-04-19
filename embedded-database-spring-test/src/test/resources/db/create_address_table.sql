create schema test;

create table test.address (
  id     bigint primary key not null,
  street varchar(255)       not null
);