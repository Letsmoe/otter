import { env } from '$env/dynamic/public';
import { configureApi } from '@neoworks-dev/otter-app/api';

const DEFAULT_API_URL = 'http://localhost:8080';

// The browser talks to the backend's public address.
configureApi(() => {
	if (env.PUBLIC_API_URL) return env.PUBLIC_API_URL;
	return DEFAULT_API_URL;
});
