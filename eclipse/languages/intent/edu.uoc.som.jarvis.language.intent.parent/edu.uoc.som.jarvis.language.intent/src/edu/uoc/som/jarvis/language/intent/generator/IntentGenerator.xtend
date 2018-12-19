/*
 * generated by Xtext 2.12.0
 */
package edu.uoc.som.jarvis.language.intent.generator

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.generator.AbstractGenerator
import org.eclipse.xtext.generator.IFileSystemAccess2
import org.eclipse.xtext.generator.IGeneratorContext
import java.util.Collections
import edu.uoc.som.jarvis.intent.IntentDefinition
import java.util.ArrayList
import java.util.List
import java.text.MessageFormat
import java.util.regex.Matcher
import java.util.regex.Pattern
import static java.util.Objects.nonNull
import edu.uoc.som.jarvis.intent.Context
import edu.uoc.som.jarvis.intent.IntentFactory
import edu.uoc.som.jarvis.intent.EntityType
import edu.uoc.som.jarvis.intent.BaseEntityDefinition

/**
 * Generates code from your model files on save.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#code-generation
 */
class IntentGenerator extends AbstractGenerator {

	private static Pattern inlineContextPattern = Pattern.compile("\\((\\w+):(\\w+)=@((?:\\w|-|_)+)\\)")
	
	private static IntentFactory factory = IntentFactory.eINSTANCE

	private static int placeholderCount = 0;
	
	private static def String getPlaceholder() {
		return "Placeholder" + placeholderCount++;
	}
	
	override void doGenerate(Resource resource, IFileSystemAccess2 fsa, IGeneratorContext context) {
		val uri = resource.URI
		var rr = resource.resourceSet.createResource(uri.trimFileExtension.appendFileExtension("xmi"))
		/*
		 * Clear the content of the resource, the output resource is created each time save is called, and may already 
		 * contain elements from a previous save.
		 */
		rr.contents.clear
		rr.contents.addAll(resource.contents)
		handleInlineContext(rr)
		rr.save(Collections.emptyMap())
	}
	
	def void handleInlineContext(Resource resource) {
		resource.allContents.filter[o|o instanceof IntentDefinition].map[o|o as IntentDefinition].forEach [ i |
			val List<String> newTrainingSentences = new ArrayList
			i.trainingSentences.forEach [ ts |
				println(MessageFormat.format("Processing training sentence {0}", ts))
				val Matcher matcher = inlineContextPattern.matcher(ts)
				if(matcher.find()) {
					val matchedGroup = matcher.group()
					println(MessageFormat.format("Found the inline pattern {0}", matchedGroup))
					val String contextName = matcher.group(1)
					val String contextParameterName = matcher.group(2)
					val String entityName = matcher.group(3)
					if(nonNull(contextName) && nonNull(contextParameterName) && nonNull(entityName)) {
						var Context context = null
						if(i.outContexts.map[cc|cc.name].contains(contextName)) {
							println(MessageFormat.format("The output context {0} already exists", contextName))
							context = i.outContexts.findFirst[cc|cc.name.equals(contextName)]
						} else {
							println(MessageFormat.format("The output context {0} does not exist, creating a new instance", contextName))
							context = factory.createContext
							context.name = contextName
						}
						if(!context.parameters.map[pp|pp.name].contains(contextParameterName)) {
							println(MessageFormat.format("Creating new output context parameter {0} (entityType={1})", contextParameterName, entityName))
							var contextParameter = factory.createContextParameter
							contextParameter.name = contextParameterName
							var baseEntityReference = IntentFactory.eINSTANCE.createBaseEntityDefinitionReference
							contextParameter.entity = baseEntityReference
							var baseEntityDefinition = IntentFactory.eINSTANCE.createBaseEntityDefinition()
							baseEntityReference.baseEntity = baseEntityDefinition
							println("Searching for entityType " + entityName.toLowerCase)
							println("Found " + EntityType.get(entityName.toLowerCase))
							(contextParameter.entity as BaseEntityDefinition).entityType = EntityType.get(entityName.toLowerCase)
							contextParameter.textFragment = placeholder
							context.parameters.add(contextParameter)
							newTrainingSentences.add(ts.replace(matchedGroup, contextParameter.textFragment))
							i.outContexts.add(context)
						} else {
							/*
							 * The context parameter already exists, this is the case if there are multiple training 
							 * sentences with inline context declarations.
							 * TODO we should throw an exception if the context parameter is different from the existing one
							 */ 
							println(MessageFormat.format("Context parameter {0}:{1} already exists, skipping it", contextName, contextParameterName))
							val fragment = context.parameters.findFirst[pp | pp.name.equals(contextParameterName)].textFragment
							newTrainingSentences.add(ts.replace(matchedGroup, fragment))
						}

					}
				} else {
					newTrainingSentences.add(ts)
				}
				i.trainingSentences.clear
				i.trainingSentences.addAll(newTrainingSentences)
			]
		]
	}
}
