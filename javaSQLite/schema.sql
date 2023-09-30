create table urls (
	shortURL varchar(20) primary key,
	longURL varchar(100)
);

insert into urls values ('test.com/hello_world', 'www.thisisaverylongurltoshorten.com/thisisanevenlongerone');
