package com.github.takemikami.rdflint;

public class LintProblem {

  private int level;
  private String message;

  public LintProblem(int level, String message) {
    this.level = level;
    this.message = message;
  }

  public int getLevel() {
    return level;
  }

  public String getMessage() {
    return message;
  }

}
