import { useQuery } from "@tanstack/react-query";

// The software release this frontend was built from (ADR-0052). The release workflow writes
// public/oxo2-release.json before building the image, so a released build knows its own version,
// DOI and history without asking GitHub or Zenodo at runtime. The checked-in file is the
// development placeholder: `current` is null, which the footer shows as "Development build".
// The same manifest is served by the backend at /api/v2/release; the frontend reads its own copy
// so the footer is right even when the backend is down or is a different build.

export interface ReleaseEntry {
    version: string;
    commit: string;
    releasedAt: string;
    notes: string;
    doi: string | null;
    releaseUrl: string;
}

export interface ReleaseManifest {
    current: ReleaseEntry | null;
    conceptDoi: string | null;
    releases: ReleaseEntry[];
}

// A public asset, so it lives under Vite's BASE_URL (always slash-terminated), not the API base.
export const releaseManifestUrl = `${import.meta.env.BASE_URL}oxo2-release.json`;

async function fetchReleaseManifest(): Promise<ReleaseManifest> {
    const response = await fetch(releaseManifestUrl);
    if (!response.ok) {
        throw new Error(`Failed to load ${releaseManifestUrl}: ${response.status}`);
    }
    return (await response.json()) as ReleaseManifest;
}

export function useReleaseManifest() {
    return useQuery({
        queryKey: ["release-manifest"],
        queryFn: fetchReleaseManifest,
        staleTime: Infinity,
        retry: false,
    });
}
