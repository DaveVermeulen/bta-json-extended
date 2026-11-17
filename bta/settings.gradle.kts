pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net") { name = "Fabric" }
		maven("https://maven.thesignalumproject.net/infrastructure") { name = "SignalumMavenInfrastructure" }
	}
	val foojayResolverVersion = providers.gradleProperty("foojay_resolver_version")
	plugins {
		id("org.gradle.toolchains.foojay-resolver-convention").version(foojayResolverVersion.get())
	}
}
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "jsonextended-bta"
