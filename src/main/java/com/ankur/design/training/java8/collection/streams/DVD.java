package com.ankur.design.training.java8.collection.streams;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.nio.file.Files.*;

public class DVD {
    private String movieName;
    private String genre;
    private String leadActor;

    public DVD() {
    }

    public DVD(String movieName, String genre, String leadActor) {
        this.movieName = movieName;
        this.genre = genre;
        this.leadActor = leadActor;
    }

    public String getMovieName() {
        return movieName;
    }

    public void setMovieName(String movieName) {
        this.movieName = movieName;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getLeadActor() {
        return leadActor;
    }

    public void setLeadActor(String leadActor) {
        this.leadActor = leadActor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DVD dvd = (DVD) o;
        return Objects.equals(getMovieName(), dvd.getMovieName()) &&
                Objects.equals(getGenre(), dvd.getGenre()) &&
                Objects.equals(getLeadActor(), dvd.getLeadActor());
    }

    @Override
    public int hashCode() {
        return Objects.hash(31, getMovieName(), getGenre(), getLeadActor());
    }

    @Override
    public String toString() {
        return "DVD{" +
                "movieName='" + movieName + '\'' +
                ", genre='" + genre + '\'' +
                ", leadActor='" + leadActor + '\'' +
                '}';
    }

    public static void main(String[] args) throws IOException {
        List<DVD> list = new ArrayList<>();
        Path path = FileSystems.getDefault().getPath("/Users/ankurbrdwj/DriveC/Java/GitHub/ankurbrdwj/Java8/src/com/ankur/training/java8/collection/streams/DVDINFO.txt");
        //Path path = FileSystems.getDefault().getPath("com/ankur/training/java8/collection/streams/DVDINFO.txt");
        //Path path = FileSystems.getDefault().getPath("./src/DVDINFO.txt");
        if(isPathValid(path.toString())){
        try (Stream<String> stream = Files.lines(Paths.get(path.toString()))) {
            stream.forEach(line -> {
                String[] info = line.split(",");
                list.add(new DVD(info[0].trim(), info[1].trim(), info[2].trim()));
            });
        }
        list.forEach(System.out::println);
    }
    }

    public static boolean isPathValid(String path) {
        File f = new File(path);
        try {
            System.out.println(f.getCanonicalPath());
            System.out.println(f.exists());
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }
}
