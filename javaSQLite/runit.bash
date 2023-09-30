#!/bin/bash

mkdir /virtual/$USER
rm /virtual/$USER/example.db
sqlite3 /virtual/$USER/example.db < schema.sql

javac DB.java
java -classpath ".:sqlite-jdbc-3.39.3.0.jar" App.java "jdbc:sqlite:/virtual/$USER/example.db"



