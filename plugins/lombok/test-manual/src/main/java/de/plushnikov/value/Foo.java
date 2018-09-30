package de.plushnikov.value;

import lombok.Value;

@Value
public class Foo {
  String one;
  String two = "fooqwewe";
  static String string = "hallo";

  public void foo() {
    one = two;
    two = "sss";
    string = "aaaa";
  }

  public static void main(String[] args) {
    System.out.println(new Foo("one"));
  }
}
