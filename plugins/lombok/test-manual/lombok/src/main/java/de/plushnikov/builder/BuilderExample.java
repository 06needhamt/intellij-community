package de.plushnikov.builder;

import lombok.experimental.Builder;

@Builder
public class BuilderExample {
    private String name;
    private int age;

    public static void main(String[] args) {
        BuilderExample example = BuilderExample.builder().age(123).name("Hallo").build();
        System.out.println(example);
    }

}
