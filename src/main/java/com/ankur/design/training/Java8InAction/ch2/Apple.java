package com.ankur.design.training.Java8InAction.ch2;

public class Apple {

	public Apple() {
		// TODO Auto-generated constructor stub
	}
private String color;
private double weight;
public String getColor() {
	return color;
}
public void setColor(String color) {
	this.color = color;
}
public double getWeight() {
	return weight;
}
public void setWeight(double weight) {
	this.weight = weight;
}
@Override
public String toString() {
	return "Apple [color=" + color + ", weight=" + weight + "]";
}
public Apple(String color, double weight) {
	super();
	this.color = color;
	this.weight = weight;
}

}
