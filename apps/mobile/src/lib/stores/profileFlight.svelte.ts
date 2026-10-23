// The avatar's flight from the profile picker into the bottom bar's Profile
// tab, started by the picker and played by ProfileFlight in the layout.

export interface Flight {
	imageUrl: string;
	from: DOMRect;
}

class ProfileFlightState {
	flight = $state<Flight | null>(null);
	/** True from takeoff until landing; the tab hides its own avatar meanwhile. */
	get inFlight(): boolean {
		return this.flight !== null;
	}

	start(imageUrl: string, from: DOMRect) {
		this.flight = { imageUrl, from };
	}

	land() {
		this.flight = null;
	}
}

export const profileFlight = new ProfileFlightState();
