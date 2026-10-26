import { Link } from "react-router";
import { useReleaseManifest } from "../../app/release";

// The software version of this build (ADR-0052): a released image links its version to the
// release notes and, when archived, its DOI; a development or local build says so. Shown without a
// placeholder while loading, and silently omitted if the manifest cannot be fetched — the footer
// must never make the page look broken over a version label.
function ReleaseVersion() {
  const { data, isError, isPending } = useReleaseManifest();
  if (isPending || isError) {
    return null;
  }
  if (!data.current) {
    return <span>Development build</span>;
  }
  const { version, releaseUrl, doi } = data.current;
  return (
      <span>
        <a href={releaseUrl} className="link-footer" title="Release notes">
          OxO2 v{version}
        </a>
        {doi && (
            <>
              {" · "}
              <a href={`https://doi.org/${doi}`} className="link-footer" title="Cite this software release">
                DOI
              </a>
            </>
        )}
      </span>
  );
}

export default function Footer() {
  return (
      <footer className="h-fit absolute inset-x-0 bottom-0 bg-embl-grey-dark border-t-8 border-link-default flex flex-col px-12 py-6 text-embl-grey-lightest">
        <div className="flex flex-row gap-10 border-b border-embl-grey-lightest mb-4 pb-8">
          <div className="flex flex-col">
            <Link
                to={"/about"}
                className="link-footer mb-1 text-xs uppercase font-bold"
            >
              About
            </Link>
            <a href={import.meta.env.REACT_APP_SSSOM_HOME} className="link-footer">
              SSSOM
            </a>
          </div>
          <div className="flex flex-col">
            <a
                href={import.meta.env.REACT_APP_SPOT_OXO2_REPO}
                className="link-footer mb-1 text-xs uppercase font-bold"
            >
              Project
            </a>
            <a
                href={`${import.meta.env.REACT_APP_SPOT_OXO2_REPO}/issues`}
                className="link-footer"
            >
              Issues / Feedback
            </a>
          </div>
        </div>
        <div className="flex flex-row gap-4 h-6 items-center">
        <span>
          <i className="icon icon-common icon-copyright icon-spacer" />
          EMBL-EBI&nbsp;2026
        </span>
          <a href={import.meta.env.REACT_APP_EBI_LICENSING} className="link-footer">
            Licensing
          </a>
          <ReleaseVersion />
        </div>
      </footer>
  );
}
