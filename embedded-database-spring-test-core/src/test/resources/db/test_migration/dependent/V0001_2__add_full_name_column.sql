alter table test.person add column full_name varchar(255);

update test.person set full_name = first_name||' '||surname