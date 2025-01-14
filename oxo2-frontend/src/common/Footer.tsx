import { Link } from "react-router-dom";

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
          <a href={process.env.REACT_APP_SSSOM_HOME} className="link-footer">
            SSSOM
          </a>
        </div>
        <div className="flex flex-col">
          <a
            href={process.env.REACT_APP_SPOT_OXO2_REPO}
            className="link-footer mb-1 text-xs uppercase font-bold"
          >
            Project
          </a>
          <a
            href={`${process.env.REACT_APP_SPOT_OXO2_REPO}/issues`}
            className="link-footer"
          >
            Issues / Feedback
          </a>
        </div>
      </div>
      <div className="flex flex-row gap-4 h-6 items-center">
        <span>
          <i className="icon icon-common icon-copyright icon-spacer" />
          EMBL-EBI&nbsp;2023
        </span>
        <a href={process.env.REACT_APP_EBI_LICENSING} className="link-footer">
          Licensing
        </a>
      </div>
    </footer>
  );
}
