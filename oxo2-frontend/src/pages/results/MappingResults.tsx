import { Search } from "../../components/search/Search";
import { SearchInput } from "../../model/Search";
import { FacetedMapping, fetchMappings, fromJson, emptyFacetedMapping } from "./MappingResultsSlice";
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from "react-router-dom";
import { useState, JSX } from "react";
import { Mapping } from "../../model/Mapping";
import urlJoin from "url-join";
import {
    EyeIcon
} from "@heroicons/react/24/solid";
import { ErrorInfo } from "../../components/error/ErrorInfo";

function MappingItem({
                         mapping,
                         navigateFn
                     }: {
    mapping: Mapping,
    navigateFn: (path: string) => void
}): JSX.Element {
    const [isSubjectCopied, setIsSubjectCopied] = useState(false);
    const [isObjectCopied, setIsObjectCopied] = useState(false);

    function EntityBox({
                           id,
                           label,
                           isCopied,
                           setCopied,
                           isLeftSide
                       }: {
        id: string;
        label: string;
        isCopied: boolean;
        setCopied: (val: boolean) => void;
        isLeftSide: boolean
    }) : JSX.Element {
        return (
            <div className={`flex-1 flex flex-col justify-center h-[5rem] px-6 py-3
                ${isLeftSide ? "rounded-2xl lg:rounded-r-none" : "rounded-2xl lg:rounded-l-none"}
                bg-grey-300 group-hover:bg-yellow-100`}>
                <div className="text-center font-bold">
                <span
                    className="pr-2 cursor-pointer hover:underline"
                    onClick={() => navigateFn(`/entity/${encodeURIComponent(id)}`)}
                >
                  {id}
                </span>
                    <i
                        title="Copy"
                        className={`icon icon-common icon-copy icon-spacer ${isCopied ? "cursor-wait" : "cursor-pointer"}`}
                        onClick={() => copyText(id, setCopied)}
                    />
                    <a
                        href={`http://www.ebi.ac.uk/ols4?termId=${encodeURIComponent(id)}`}
                        title={`View ${id} in OLS`}
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        <img
                            src={urlJoin(import.meta.env.PUBLIC_URL || "", "/logo.svg")}
                            alt="OLS"
                            className="h-6 w-6 inline-block icon-spacer"
                        />
                    </a>
                </div>
                <div title={label} className="text-center truncate">{label}</div>
            </div>
        );
    };

    return (
        <div className="group mb-6 text-neutral-black flex flex-col items-stretch items-center lg:flex-row w-full
            hover:shadow-lg hover:rounded-2xl transition-all duration-200">
            <div className="w-full lg:w-1/3">
                <EntityBox
                    id={mapping.subjectId}
                    label={mapping.subjectLabel}
                    isCopied={isSubjectCopied}
                    setCopied={setIsSubjectCopied}
                    isLeftSide={true}
                />
            </div>

            <div className="flex-none flex flex-col justify-center h-[5rem] px-6 py-3 my-2 lg:my-0 w-full lg:w-1/3 rounded-2xl lg:rounded-none
                bg-neutral-light group-hover:bg-yellow-50">
                <div title={mapping.predicateId} className="text-center font-bold">{mapping.predicateId}</div>
                <div title={mapping.predicateLabel} className="text-center truncate">{mapping.predicateLabel}</div>
            </div>

            <div className="w-full lg:w-1/3">
                <EntityBox
                    id={mapping.objectId}
                    label={mapping.objectLabel}
                    isCopied={isObjectCopied}
                    setCopied={setIsObjectCopied}
                    isLeftSide={false}
                />
            </div>

            <div
                className="link-default text-center cursor-pointer self-center my-2 mx-4"
                onClick={() => navigateFn(`/mapping/${encodeURIComponent(mapping.mappingId)}`)}
                title="View mapping details"
            >
                <EyeIcon className="h-5 w-5" />
            </div>
        </div>
    );
}

function MappingResults(searchInput: SearchInput) {
    const navigate = useNavigate();

    const { data, isLoading, error } = useQuery({
        queryKey: ["fetchMappings", searchInput.sanitizedSearchInput],
        queryFn: () => fetchMappings(searchInput.sanitizedSearchInput)
    });

    // Process data
    const mappingResponse: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    return (
        <div>
            <Search searchInput={searchInput} />

            {isLoading && (
                <div className="flex justify-center p-8">
                    <div className="spinner-border text-primary" role="status">
                        Loading...
                    </div>
                </div>
            )}

            {error &&
                <ErrorInfo task={"fetching mappings"} message={error.message}/>
            }

            {!isLoading && !error && mappingResponse.mappings.length === 0 && (
                <div className="bg-blue-100 text-blue-700 p-4 rounded-lg my-4">
                    No mapping results found for your search.
                </div>
            )}

            {!isLoading && !error && mappingResponse.mappings.length > 0 && (
                <ul>
                    {mappingResponse.mappings.map((mapping) => (
                        <MappingItem
                            key={mapping.mappingId}
                            mapping={mapping}
                            navigateFn={navigate}
                        />
                    ))}
                </ul>
            )}
        </div>
    );
}


export async function copyToClipboard(text: string) {
    if ("clipboard" in navigator) {
        return await navigator.clipboard.writeText(text);
    } else {
        return document.execCommand("copy", true, text);
    }
}

function copyText(text: string, setToggle: (toggle: boolean) => void) {
    copyToClipboard(text)
        .then(() => {
            setToggle(true);
            setTimeout(() => {
                setToggle(false);
            }, 500);
        })
        .catch((err) => {
            console.log(err);
        });
}

export default MappingResults;