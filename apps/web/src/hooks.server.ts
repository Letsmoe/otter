import { configureApi } from '@neoworks-dev/otter-app/api';

const DEFAULT_API_URL = 'http://localhost:8080';

// SSR talks to the backend over the internal network address.
configureApi(() => {
	const internalBase = process.env.INTERNAL_API_URL;
	if (internalBase) return internalBase;
	return DEFAULT_API_URL;
});
