import {useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {Search} from "../../components/search/Search";
import {SearchInput} from "../../model/Search";
import {emptyFacetedMapping, FacetedMapping, fetchMappings, fromJson} from "./MappingResultsSlice";
import {useQuery} from "@tanstack/react-query";
import {MaterialReactTable, type MRT_ColumnDef, MRT_PaginationState, useMaterialReactTable} from 'material-react-table';
import {Mapping} from "../../model/Mapping.ts";


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

    const [columnFilters, setColumnFilters] = useState<any[]>([]);

    const { data, isLoading, isError } = useQuery({
        queryKey: [
            "fetchMappings",
            searchInput.sanitizedSearchInput,
            pagination.pageIndex,
            pagination.pageSize,
            columnFilters
        ],
        queryFn: () =>
            fetchMappings(
                searchInput.sanitizedSearchInput,
                pagination.pageIndex,
                pagination.pageSize,
                columnFilters // Pass filters to fetchMappings
            ),
        staleTime: Infinity,
    });

    const mappingResults: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    const columns = useMemo<MRT_ColumnDef<Mapping>[]>(
        () => [
            {
               accessorKey: "mappingId",
               header: "Mapping Id"
            },
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

    const [columnVisibility, setColumnVisibility] = useState({ mappingId: false })

    const table = useMaterialReactTable({
        columns,
        data: mappingResults.mappings,
        manualPagination: true, //turn off built-in client-side pagination
        manualFiltering: true, // Enable manual filtering
        //give loading spinner somewhere to go while loading
        muiTableBodyProps: {
            children: isLoading ? (
                <tr style={{ height: '200px' }}>
                    <td />
                </tr>
            ) : undefined,
            sx: {
                //stripe the rows, make odd rows a darker color
                '& tr:nth-of-type(even) > td': {
                    backgroundColor: '#F3F4F6',
                },
            },
        },
        muiToolbarAlertBannerProps: isError
            ? {
                color: 'error',
                children: 'Error loading data',
            }
            : undefined,
        muiPaginationProps: {
            sx: { justifyContent: 'center', display: 'flex' }
        },
        onPaginationChange: setPagination,
        onColumnFiltersChange: setColumnFilters,
        rowCount: (mappingResults?.totalElements) ?? 0,
        state: {
            isLoading,
            pagination,
            columnFilters, // Pass filter state to table
            showAlertBanner: isError,
            columnVisibility
        },
        enableFilterMatchHighlighting: true,
        enableGlobalFilter: false,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        enableHiding: false,
        enableTopToolbar: false,
        muiTableBodyRowProps: ({ row }) => ({
            onClick: () => {
                const mapping = row.original;
                navigate(`/mapping/${mapping.mappingId}`, { state: { mapping } });
            },
            style: { cursor: 'pointer' }
        }),
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