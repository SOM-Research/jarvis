/*
 * generated by Xtext 2.12.0
 */
package com.xatkit.language.platform.ide

import com.google.inject.Guice
import com.xatkit.language.platform.PlatformRuntimeModule
import com.xatkit.language.platform.PlatformStandaloneSetup
import org.eclipse.xtext.util.Modules2

/**
 * Initialization support for running Xtext languages as language servers.
 */
class PlatformIdeSetup extends PlatformStandaloneSetup {

	override createInjector() {
		Guice.createInjector(Modules2.mixin(new PlatformRuntimeModule, new PlatformIdeModule))
	}
	
}
