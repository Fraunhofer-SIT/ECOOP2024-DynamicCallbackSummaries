package reproduction.dynamiccallbackidentification.util;

import com.esotericsoftware.kryo.Kryo;

import de.codeinspect.serialization.IMultipleSerializerRegistration;
import reproduction.dynamiccallbackidentification.loggingpoints.DCIDCallbackCalledLoggingPoint;

public class SerializerConfig implements IMultipleSerializerRegistration {

	@Override
	public void register(Kryo kryo, de.codeinspect.serialization.SerializerConfig config) {
		int start = 900;
		kryo.register(DCIDCallbackCalledLoggingPoint.class, start++);
	}

}
