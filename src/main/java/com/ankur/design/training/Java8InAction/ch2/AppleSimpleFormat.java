package com.ankur.design.training.Java8InAction.ch2;

public class AppleSimpleFormat implements ApplePredicate {

	public AppleSimpleFormat() {
		// TODO Auto-generated constructor stub
	}

	public String test(Apple a) {
		// TODO Auto-generated method stub
		return "An apple of "+a.getWeight()+" gm .";
	}

	public String test(Object a) {
		// TODO Auto-generated method stub
		return null;
	}

}
