import { InfoCard } from "../../components/infoCard/InfoCard";
import { Search } from "../../components/search/Search";
import { ExclamationTriangleIcon, WrenchIcon, InformationCircleIcon } from "@heroicons/react/24/solid";
import {JSX} from "react";


export default function Home(): JSX.Element {
    const iconClass = "w-6 h-6 text-yellow-default";

    return (
        <main className="container mx-auto">
            <div className="grid grid-cols-1 lg:grid-cols-4 lg:gap-8">
                <div className="lg:col-span-3">
                    <Search
                        userSearchInput= ""
                        sanitizedSearchInput = {[]}
                    />
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
                        <InfoCard
                            icon={<InformationCircleIcon className={iconClass}/>}
                            title="About OxO"
                            link="/about"
                            description="OxO is a service for finding mappings between entities based on the {link}. OxO is developed and maintained by the Samples, Phenotypes and Ontologies Team (SPOT) at {link}."
                            externalLinks={[
                                { href: import.meta.env.REACT_APP_SSSOM_HOME, text: "Simple Standard for Sharing Ontological Mappings (SSSOM)" },
                                { href: import.meta.env.REACT_APP_EBI_HOME, text: "EMBL-EBI" }
                            ]}
                        />
                        <InfoCard
                            icon={<WrenchIcon className={iconClass}/>}
                            title="Related Tools"
                            link={import.meta.env.REACT_APP_SPOT_ONTOTOOLS}
                            description="In addition to OxO, SPOT also provides {link} and {link} services. OLS provides access to the latest ontology versions. ZOOMA is a service to assist in mapping strings of text to ontology terms in OLS."
                            externalLinks={[
                                { href: import.meta.env.REACT_APP_SPOT_OLS, text: "OLS" },
                                { href: import.meta.env.REACT_APP_SPOT_ZOOMA, text: "ZOOMA" }
                            ]}
                        />
                        <InfoCard
                            icon={<ExclamationTriangleIcon className={iconClass}/>}
                            title="Report an Issue"
                            link={`${import.meta.env.REACT_APP_SPOT_OXO2_REPO}/issues`}
                            description="For feedback, suggestion or requests about OxO please use our {link}. For announcements relating to OxO, such as new releases and new features sign up to the {link}."
                            externalLinks={[
                                { href: `${import.meta.env.REACT_APP_SPOT_OXO2_REPO}/issues`, text: "GitHub issue tracker" },
                                { href: import.meta.env.REACT_APP_SPOT_OLS_ANNOUNCE, text: "OLS announce mailing list" }
                            ]}
                        />
                    </div>
                </div>
            </div>
        </main>
    );
}
