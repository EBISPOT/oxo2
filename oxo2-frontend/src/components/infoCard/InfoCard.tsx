import React from "react";
import {Link} from "react-router-dom";
import {InfoCardProps} from "./InfoCardSlice";
import {JSX} from "react";

export function InfoCard(infoCardProps: InfoCardProps):  JSX.Element {
    const { description, externalLinks } = infoCardProps;

    const renderDescription = () => {
        if (!externalLinks || externalLinks.length === 0) {
            return description;
        }

        return description.split("{link}").map((part, index) => (
            <React.Fragment key={index}>
                &thinsp;{part}&thinsp;
                {externalLinks[index] && (
                    <a href={externalLinks[index].href || "#"} className="link-default">
                        {externalLinks[index].text}
                    </a>
                )}
            </React.Fragment>
        ));
    };

    return (
        <div className="px-2">
            <div className="flex flex-col items-center justify-center max-h-screen text-2xl mb-3 text-neutral-default">
                <div className="flex items-center space-x-4 p-4 w-full">
                    <span>
                    {infoCardProps.icon && React.isValidElement(infoCardProps.icon)? (
                        infoCardProps.icon
                    ) : (
                        <></>
                    )}
                    </span>
                    <span>
                    {infoCardProps.link ?  (
                        <Link to={infoCardProps.link} className="link-header">
                            {infoCardProps.title}
                        </Link>
                    ) : (
                        <span className="link-header">{infoCardProps.title}</span>
                    )}
                    </span>
                </div>
            </div>
            <p className="text-tertiary">
                {renderDescription()}
            </p>
        </div>
    )
};
