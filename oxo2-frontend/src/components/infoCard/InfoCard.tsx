import React from "react";
import {Link} from "react-router";
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
            <div className="flex flex-col items-center justify-center max-h-screen mb-2 text-neutral-default">
                <div className="flex items-center space-x-3 py-2 w-full">
                    <span>
                    {infoCardProps.icon && React.isValidElement(infoCardProps.icon)? (
                        infoCardProps.icon
                    ) : (
                        <></>
                    )}
                    </span>
                    <span>
                    {infoCardProps.link ?  (
                        <Link to={infoCardProps.link} className="link-header text-xl">
                            {infoCardProps.title}
                        </Link>
                    ) : (
                        <span className="link-header text-xl">{infoCardProps.title}</span>
                    )}
                    </span>
                </div>
            </div>
            <p className="text-neutral-dark text-lg leading-7 [&_.link-default]:text-lg">
                {renderDescription()}
            </p>
        </div>
    )
};
