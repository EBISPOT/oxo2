import { Search } from "../../components/search/Search";
import { SearchInput } from "../../model/Search";
import {FacetedMapping, fetchMappings, fromJson} from "./MappingResultsSlice.ts";
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from "react-router-dom";
import { useState } from "react";

function MappingResults(searchInput: SearchInput) {
    const navigate = useNavigate();
    const [isSubjectCopied, setIsSubjectCopied] = useState(false);
    const [isObjectCopied, setIsObjectCopied] = useState(false);
    const [focusMappingId, setFocusMappingId] = useState<string|undefined>(undefined);

    const { data, isLoading, error } = useQuery({
        queryKey: ["fetchMappings"],
        queryFn: () => fetchMappings(searchInput.sanitizedSearchInput)
    });

    if (isLoading) return <div>Loading...</div>;
    if (error) return <div>Error: {error.message}</div>;
    const mappingResponse: FacetedMapping = fromJson(data);
    if (!focusMappingId && mappingResponse.mappings.length > 0) {
        setFocusMappingId(mappingResponse.mappings[0].mappingId);
    }

    return (
        <div>
            <Search
                searchInput = {searchInput}
            />

                <ul>

                    {   mappingResponse.mappings &&
                        mappingResponse.mappings.map((mapping) => (
                            <div
                                key={mapping.mappingId}
                                className="mb-6 text-neutral-black flex flex-col items-stretch items-center lg:flex-row"
                            >
                                <div
                                    className={`flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-l-2xl lg:rounded-r-none ${
                                        mapping.subjectId === focusMappingId
                                            ? "bg-yellow-300"
                                            : "bg-grey-300"
                                    }`}
                                >
                                    <div className="text-center font-bold">
                                        <span
                                            className={`pr-2 ${
                                                mapping.subjectId === focusMappingId
                                                    ? ""
                                                    : "cursor-pointer hover:underline"
                                            }`}
                                            onClick={() => {
                                                navigate(
                                                    `/entity/${encodeURIComponent(
                                                        mapping.subjectId || ""
                                                    )}`
                                                );
                                            }}
                                        >
                                          {mapping.subjectId}
                                        </span>
                                        <i
                                            title="Copy"
                                            className={`icon icon-common icon-copy icon-spacer ${
                                                isSubjectCopied ? "cursor-wait" : "cursor-pointer"
                                            }`}
                                            onClick={() => {
                                                copyText(mapping.subjectId, setIsSubjectCopied);
                                            }}
                                        />
                                        <a
                                            href={mapping.subjectId}
                                            title={mapping.subjectId}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                        >
                                            <i className="icon icon-common icon-external-link-alt icon-spacer" />
                                        </a>
                                    </div>
                                    <div
                                        title={mapping.subjectLabel}
                                        className="text-center truncate"
                                    >
                                        {mapping.subjectLabel || ""}
                                    </div>
                                </div>
                                <div
                                    className={`w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-y-neutral-light ${
                                        mapping.subjectLabel === focusMappingId
                                            ? "lg:border-l-yellow-300"
                                            : "lg:border-l-grey-300"
                                    }`}
                                ></div>
                                <div className="flex-none flex flex-col justify-center lg:min-w-0 lg:h-[5rem] bg-neutral-light px-6 py-3 rounded-2xl lg:rounded-none">
                                    <div
                                        title={mapping.predicateId}
                                        className="text-center font-bold"
                                    >
                                        {mapping.predicateId}
                                    </div>
                                    <div
                                        title={mapping.predicateLabel}
                                        className="text-center truncate"
                                    >
                                        {mapping.predicateLabel || ""}
                                    </div>
                                </div>
                                <div
                                    className={`w-0 icon icon-common icon-arrow-down self-center lg:h-0 lg:text-transparent lg:flex-none lg:border-y-[2.5rem] lg:border-l-[1rem] lg:border-l-neutral-light ${
                                        mapping.objectId === focusMappingId
                                            ? "lg:border-y-yellow-300"
                                            : "lg:border-y-grey-300"
                                    }`}
                                ></div>
                                <div
                                    className={`flex-1 flex flex-col justify-center lg:min-w-0 h-[5rem] px-6 py-3 rounded-2xl lg:rounded-r-2xl lg:rounded-l-none ${
                                        mapping.objectId === focusMappingId
                                            ? "bg-yellow-300"
                                            : "bg-grey-300"
                                    }`}
                                >
                                    <div className="text-center font-bold">
                                        <span
                                            className={`pr-2 ${
                                                mapping.objectId === focusMappingId
                                                    ? ""
                                                    : "cursor-pointer hover:underline"
                                            }`}
                                            onClick={() => {
                                                navigate(
                                                    `/entity/${encodeURIComponent(
                                                        mapping.objectId || ""
                                                    )}`
                                                );
                                            }}
                                        >
                                          {mapping.objectId}
                                        </span>
                                        <i
                                            title="Copy"
                                            className={`icon icon-common icon-copy icon-spacer ${
                                                isObjectCopied ? "cursor-wait" : "cursor-pointer"
                                            }`}
                                            onClick={() => {
                                                copyText(mapping.objectId, setIsObjectCopied);
                                            }}
                                        />
                                        <a
                                            href={mapping.objectId}
                                            title={mapping.objectId}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                        >
                                            <i className="icon icon-common icon-external-link-alt icon-spacer" />
                                        </a>
                                    </div>
                                    <div
                                        title={mapping.objectLabel}
                                        className="text-center truncate"
                                    >
                                        {mapping.objectLabel || ""}
                                    </div>
                                </div>
                                <div
                                    className="link-default text-sm font-bold text-center cursor-pointer self-center my-2 mx-4"
                                    onClick={() => {
                                        navigate(
                                            `/mapping/${encodeURIComponent(mapping.mappingId)}`
                                        );
                                    }}
                                >
                                    View
                                </div>
                            </div>

                    ))
                    }
                </ul>
        </div>
    );
};

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
            // revert after a few seconds
            setTimeout(() => {
                setToggle(false);
            }, 500);
        })
        .catch((err) => {
            console.log(err);
        });
}
export default MappingResults;
