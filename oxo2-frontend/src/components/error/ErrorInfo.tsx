import {ExternalLink, InfoCardProps} from "../infoCard/InfoCardSlice";
import {Link} from "react-router-dom";
import {ErrorProps} from "./ErrorSlice";

export function ErrorInfo(error: ErrorProps) {
    return (
        <div className="px-2">
            <div className="text-2xl mb-3 text-neutral-default">
                <i className={`icon icon-common ${infoCardProps.iconClass} icon-spacer text-yellow-default`}/>
                {infoCardProps.link ?  (
                    <Link to={infoCardProps.link} className="link-default">
                        {infoCardProps.title}
                    </Link>
                ) : (
                    <>{infoCardProps.title}</>
                )}
            </div>
            <p>
                {infoCardProps.description}
                {infoCardProps.externalLinks && infoCardProps.externalLinks.map((link: ExternalLink, index: React.Key) => (
                    <React.Fragment key={index}>
                        &thinsp;
                        <a href={link.href || "#"} className="link-default">
                            {link.text}
                        </a>
                    </React.Fragment>
                ))}
            </p>
        </div>
    )
};