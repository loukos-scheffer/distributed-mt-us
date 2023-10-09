package utils;// this is a class to help us interpret the database rows when processing output, it is not a schema

public class Row {
    private String shortURL = null;
    private String longURL = null;

    public Row(String shortURL, String longURL) {
        this.shortURL = shortURL;
        this.longURL = longURL;
    }

    public String getShortURL(){
        return this.shortURL;
    }

    public String getLongURL(){
        return this.longURL;
    }
}