package com.ankur.design.training.Java8InAction.ch2;

public class AppleFancyFormat implements ApplePredicate {

	public AppleFancyFormat() {
		// TODO Auto-generated constructor stub
	}

	public String test(Apple a) {
		// TODO Auto-generated method stub
		return "A "+(a.getWeight()>150?" heavy ":" light ")+ a.getColor()+ " apple .";
	}

	public String test(Object a) {
		// TODO Auto-generated method stub
		return null;
	}

}
