import { useEffect, useState } from 'react';
import { request } from '../api/client';

export type Branding = { organizationName: string; hasLogo: boolean };
export const DEFAULT_BRANDING: Branding = { organizationName: 'B2BGearVia', hasLogo: false };
export const BRANDING_LOGO_URL = '/api/v1/branding/logo';

let cached: Branding | null = null;
let inFlight: Promise<Branding> | null = null;

function load(): Promise<Branding> {
  if (cached) return Promise.resolve(cached);
  if (!inFlight) {
    inFlight = request<Branding>('/branding', {}, false)
      .then((value) => { cached = value; return value; })
      .catch(() => DEFAULT_BRANDING)
      .finally(() => { inFlight = null; });
  }
  return inFlight;
}

/** Public, unauthenticated organization name + logo — safe to call from the login screen. */
export function useBranding(): Branding {
  const [branding, setBranding] = useState<Branding>(cached ?? DEFAULT_BRANDING);
  useEffect(() => { load().then(setBranding); }, []);
  return branding;
}

/** Called after an admin saves branding so the next render picks up the change without a reload. */
export function refreshBranding() {
  cached = null;
  return load();
}
