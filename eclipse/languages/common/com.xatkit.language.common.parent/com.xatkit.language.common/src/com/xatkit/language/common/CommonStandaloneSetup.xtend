/*
 * generated by Xtext 2.15.0
 */
package com.xatkit.language.common


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
class CommonStandaloneSetup extends CommonStandaloneSetupGenerated {

	def static void doSetup() {
		new CommonStandaloneSetup().createInjectorAndDoEMFRegistration()
	}
}
