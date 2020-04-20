package pinboard

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(PinboardProperties::class)
class PinboardClientAutoConfiguration {

	private val log = LogFactory.getLog(javaClass)

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(value = arrayOf(RestTemplate::class))
	fun restTemplatePinboardClient(properties: PinboardProperties,
	                               objectProvider: ObjectProvider<RestTemplate>
	): PinboardClient {
		val rt = objectProvider.getIfAvailable {
			log.debug("no RestTemplate provided. Configuring a default one.")
			RestTemplate()
		}
		return PinboardClient(properties.token, rt)
	}


}