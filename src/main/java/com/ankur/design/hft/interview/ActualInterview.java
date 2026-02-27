package com.ankur.design.hft.interview;

import java.util.Objects;

public class ActualInterview {
}
class Book{
    private String title;
    private String firstAuthor;
    private String secondAuthor;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return title.equalsIgnoreCase(book.title) && (Objects.equals(firstAuthor, book.firstAuthor)
                && (Objects.equals(secondAuthor, book.secondAuthor)
                ||Objects.equals(firstAuthor, book.secondAuthor)
                && (Objects.equals(secondAuthor, book.firstAuthor );
    }

    public int hashCode1() {
        return 31 + title.toUpperCase().hashCode()+ firstAuthor.hashCode()+ secondAuthor.hashCode();
    }

    public int hashCode2() {
        return 31 + title.hashCode()+ firstAuthor.hashCode() + secondAuthor.hashCode();
    }

    public int hashCode3() {
        return 31 +title.toLowerCase().hashCode()+ firstAuthor.hashCode()+ secondAuthor.hashCode();
    }

    public int hashCode4() {
        return Objects.hash(title, firstAuthor, secondAuthor);
    }

    public int hashCode5() {
        int hash= 31;
        hash= 31* hash+Objects.hash(title);
        hash= 31* hash+Objects.hash(firstAuthor);
        hash= 31* hash+Objects.hash(secondAuthor);
        return hash;
    }

}