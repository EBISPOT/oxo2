import {ReactNode} from "react";

export interface ExternalLink {
    href: string | undefined;
    text: string;
}

export interface InfoCardProps {
    // See: https://github.com/tailwindlabs/heroicons/issues/64#issuecomment-1937098040
    icon: ReactNode;
    title: string;
    link: string | undefined;
    description: string;
    externalLinks?: ExternalLink[];
}