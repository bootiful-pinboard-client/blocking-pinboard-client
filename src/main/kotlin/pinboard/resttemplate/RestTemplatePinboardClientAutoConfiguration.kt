package pinboard.resttemplate

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import pinboard.PinboardProperties

@Configuration
@EnableConfigurationProperties(PinboardProperties::class)
class RestTemplatePinboardClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(value = arrayOf(RestTemplate::class))
	fun restTemplatePinboardClient(properties: PinboardProperties): RestTemplatePinboardClient =
			RestTemplatePinboardClient(properties.token)


}