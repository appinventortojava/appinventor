package org.appinventor.ai_test.test;

import com.google.devtools.simple.runtime.components.HandlesEventDispatching;
import com.google.devtools.simple.runtime.components.android.Button;
import com.google.devtools.simple.runtime.components.android.Clock;
import com.google.devtools.simple.runtime.components.android.Form;
import com.google.devtools.simple.runtime.components.Component;

public class Screen1 extends Form implements HandlesEventDispatching
{
	
	
	private Clock Clock1;
	private Button Button1;
	
	void $define()
	{
		
		Clock1 = new Clock( this );
		
		Button1 = new Button( this );
		Button1.Text( "Text for Button1" );
		
		
	}
	public boolean dispatchEvent( Component component, String componentName, String eventName, Object[] params )
	{
		return false;
	}
}
