package pinboard


import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "pinboard")
data class PinboardProperties(val token: String)