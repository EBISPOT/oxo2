import { useState, useEffect, useMemo } from "react";
import { useParams } from "react-router-dom";
import { Search } from "../../components/search/Search";
import { SearchInput } from "../../model/Search";
import { FacetedMapping, fetchMappings, fromJson, emptyFacetedMapping } from "./MappingResultsSlice";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { MaterialReactTable, useMaterialReactTable, type MRT_ColumnDef, MRT_PaginationState } from 'material-react-table';
import { Mapping } from "../../model/Mapping.ts";



function MappingResults() {
    const navigate = useNavigate();

    const [pagination, setPagination] = useState<MRT_PaginationState>({
        pageIndex: 0,
        pageSize: 10,
    });
    const { curies } = useParams<{ curies: string }>();
    const searchInput: SearchInput = {
        userSearchInput: curies || "",
        sanitizedSearchInput: curies
            ? curies.split(/[\n,]+/).filter((item) => item.trim() !== "")
            : [],
    };


    const { data, isLoading, isError } = useQuery({
        queryKey: ["fetchMappings", searchInput.sanitizedSearchInput, pagination.pageIndex, pagination.pageSize],
        queryFn: () => fetchMappings(
            searchInput.sanitizedSearchInput, pagination.pageIndex, pagination.pageSize),
        staleTime: Infinity,
    });

    const mappingResults: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    const columns = useMemo<MRT_ColumnDef<Mapping>[]>(
        () => [
            {
                accessorKey: "subjectId",
                header: "Subject Id",
            },
            {
                accessorKey: "predicateId",
                header: "Predicate Id",
            },
            {
                accessorKey: "objectId",
                header: "Object Id",
            },
            {
                accessorKey: "mappingJustification",
                header: "Mapping Justification",
            },
        ],
        []
    );

    const table = useMaterialReactTable({
        columns,
        data: mappingResults.mappings,
        manualPagination: true, //turn off built-in client-side pagination
        //give loading spinner somewhere to go while loading
        muiTableBodyProps: {
            children: isLoading ? (
                <tr style={{ height: '200px' }}>
                    <td />
                </tr>
            ) : undefined,
        },
        muiToolbarAlertBannerProps: isError
            ? {
                color: 'error',
                children: 'Error loading data',
            }
            : undefined,
        onPaginationChange: setPagination,
        rowCount: (mappingResults?.totalElements) ?? 0,
        state: {
            isLoading,
            pagination,
            showAlertBanner: isError
        }
    });

    return (
        <div>
            <Search searchInput={searchInput} />

            {isLoading && (
                <div className="flex justify-center p-8">
                    <div className="spinner-default w-10 h-10 animate-spin" role="status">
                        <span className="sr-only">Loading...</span>
                    </div>
                </div>
            )}

            <MaterialReactTable table={table}/>

        </div>
    );
}

export default MappingResults;